using FluentValidation;
using Identity.Application.Abstractions;
using Identity.Application.Auth.Login;
using Identity.Domain.Entities;
using MediatR;

namespace Identity.Application.Auth.Refresh;

/// <summary>
/// Trade an unrevoked, unexpired refresh token for a fresh access +
/// refresh pair. The previous refresh token is revoked atomically with
/// issuance — sliding window rotation, single-use semantics.
/// </summary>
public sealed record RefreshTokenCommand(string RefreshToken)
    : IRequest<LoginResponse>;

public sealed class RefreshTokenCommandValidator : AbstractValidator<RefreshTokenCommand>
{
    public RefreshTokenCommandValidator()
    {
        RuleFor(x => x.RefreshToken).NotEmpty();
    }
}

public sealed class InvalidRefreshTokenException : Exception
{
    public InvalidRefreshTokenException() : base("Refresh token is invalid, expired, or revoked.") { }
}

internal sealed class RefreshTokenCommandHandler(
    IRefreshTokenRepository refreshTokenRepository,
    IUserRepository userRepository,
    ITokenService tokenService) : IRequestHandler<RefreshTokenCommand, LoginResponse>
{
    public async Task<LoginResponse> Handle(RefreshTokenCommand request, CancellationToken ct)
    {
        var hash = RefreshToken.Hash(request.RefreshToken);
        var existing = await refreshTokenRepository.FindByHashAsync(hash, ct)
                       ?? throw new InvalidRefreshTokenException();

        var now = DateTime.UtcNow;
        // Either branch is "treat as forgery" — same response so we don't
        // leak which check failed.
        if (!existing.IsActive(now))
            throw new InvalidRefreshTokenException();

        var user = await userRepository.FindByIdAsync(existing.UserId, ct)
                   ?? throw new InvalidRefreshTokenException();
        if (!user.IsActive)
            throw new InvalidRefreshTokenException();

        // Same role placeholder as Login — keep these two paths in sync.
        var roles = new[] { "user" };

        existing.Revoke();
        var fresh = tokenService.Issue(user, roles);
        var stored = RefreshToken.Issue(
            user.TenantId,
            user.Id,
            fresh.RefreshToken,
            fresh.RefreshTokenExpiresAt);
        await refreshTokenRepository.AddAsync(stored, ct);
        // One SaveChanges = old-revoke + new-insert land in a single tx.
        await refreshTokenRepository.SaveChangesAsync(ct);

        return new LoginResponse(
            fresh.AccessToken,
            fresh.AccessTokenExpiresAt,
            fresh.RefreshToken,
            fresh.RefreshTokenExpiresAt);
    }
}
