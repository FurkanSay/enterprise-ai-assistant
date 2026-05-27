using FluentValidation;
using Identity.Application.Abstractions;
using Identity.Domain.Entities;
using MediatR;

namespace Identity.Application.Auth.Login;

public sealed record LoginCommand(string Email, string Password)
    : IRequest<LoginResponse>;

public sealed record LoginResponse(
    string AccessToken,
    DateTime AccessTokenExpiresAt,
    string RefreshToken,
    DateTime RefreshTokenExpiresAt);

public sealed class LoginCommandValidator : AbstractValidator<LoginCommand>
{
    public LoginCommandValidator()
    {
        RuleFor(x => x.Email).NotEmpty().EmailAddress();
        RuleFor(x => x.Password).NotEmpty().MinimumLength(8);
    }
}

internal sealed class LoginCommandHandler(
    IUserRepository userRepository,
    IPasswordHasher passwordHasher,
    ITokenService tokenService,
    IRefreshTokenRepository refreshTokenRepository) : IRequestHandler<LoginCommand, LoginResponse>
{
    public async Task<LoginResponse> Handle(LoginCommand request, CancellationToken ct)
    {
        var user = await userRepository.FindByEmailAsync(request.Email, ct)
                   ?? throw new InvalidCredentialsException();

        if (!user.IsActive)
            throw new InvalidCredentialsException();

        if (!passwordHasher.Verify(request.Password, user.PasswordHash))
            throw new InvalidCredentialsException();

        // TODO: resolve user roles from repo
        var roles = new[] { "user" };

        var tokens = tokenService.Issue(user, roles);

        // Persist the hash of the raw refresh token so /auth/refresh can
        // verify + rotate it later. We never store the raw token.
        var stored = RefreshToken.Issue(
            user.TenantId,
            user.Id,
            tokens.RefreshToken,
            tokens.RefreshTokenExpiresAt);
        await refreshTokenRepository.AddAsync(stored, ct);
        await refreshTokenRepository.SaveChangesAsync(ct);

        return new LoginResponse(
            tokens.AccessToken,
            tokens.AccessTokenExpiresAt,
            tokens.RefreshToken,
            tokens.RefreshTokenExpiresAt);
    }
}

public sealed class InvalidCredentialsException : Exception
{
    public InvalidCredentialsException() : base("Invalid email or password.") { }
}
