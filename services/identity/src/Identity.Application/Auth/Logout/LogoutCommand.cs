using FluentValidation;
using Identity.Application.Abstractions;
using Identity.Domain.Entities;
using MediatR;

namespace Identity.Application.Auth.Logout;

/// <summary>
/// Revoke a refresh token. Idempotent — a missing or already-revoked
/// token is treated as success so the frontend can call this unconditionally
/// from its logout handler without ever leaking auth state.
/// </summary>
public sealed record LogoutCommand(string RefreshToken) : IRequest<Unit>;

public sealed class LogoutCommandValidator : AbstractValidator<LogoutCommand>
{
    public LogoutCommandValidator()
    {
        RuleFor(x => x.RefreshToken).NotEmpty();
    }
}

internal sealed class LogoutCommandHandler(
    IRefreshTokenRepository refreshTokenRepository)
    : IRequestHandler<LogoutCommand, Unit>
{
    public async Task<Unit> Handle(LogoutCommand request, CancellationToken ct)
    {
        var hash = RefreshToken.Hash(request.RefreshToken);
        var existing = await refreshTokenRepository.FindByHashAsync(hash, ct);
        if (existing is null) return Unit.Value;

        existing.Revoke();
        await refreshTokenRepository.SaveChangesAsync(ct);
        return Unit.Value;
    }
}
