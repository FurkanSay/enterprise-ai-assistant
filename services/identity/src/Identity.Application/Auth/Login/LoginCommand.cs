using FluentValidation;
using Identity.Application.Abstractions;
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
    ITokenService tokenService) : IRequestHandler<LoginCommand, LoginResponse>
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
