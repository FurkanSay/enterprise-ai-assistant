using FluentValidation;
using Identity.Application.Abstractions;
using Identity.Domain.Entities;
using MediatR;

namespace Identity.Application.Auth.Register;

/// <summary>
/// Registers a brand-new user under an existing tenant. The tenant must
/// already exist — Identity does not provision tenants from this endpoint
/// (that is an admin-only flow we have not built yet).
///
/// Design note: we deliberately do NOT issue tokens on register. The user
/// must then call /v1/auth/login. Two reasons:
///   1. Keeps the registration response stable when we later add email
///      verification — the verification flow inserts a step between
///      register and first login.
///   2. Audit trails are easier to read when login is a separate event.
/// </summary>
public sealed record RegisterCommand(
    Guid TenantId,
    string Email,
    string Password,
    string DisplayName)
    : IRequest<RegisterResponse>;

public sealed record RegisterResponse(Guid UserId, string Email, Guid TenantId);

public sealed class RegisterCommandValidator : AbstractValidator<RegisterCommand>
{
    public RegisterCommandValidator()
    {
        RuleFor(x => x.TenantId).NotEqual(Guid.Empty);
        RuleFor(x => x.Email).NotEmpty().EmailAddress().MaximumLength(320);
        RuleFor(x => x.Password)
            .NotEmpty()
            .MinimumLength(8)
            .MaximumLength(128);
        RuleFor(x => x.DisplayName).MaximumLength(120);
    }
}

internal sealed class RegisterCommandHandler(
    IUserRepository userRepository,
    IPasswordHasher passwordHasher)
    : IRequestHandler<RegisterCommand, RegisterResponse>
{
    public async Task<RegisterResponse> Handle(RegisterCommand request, CancellationToken ct)
    {
        var existing = await userRepository.FindByEmailAsync(request.Email, ct);
        if (existing is not null)
            throw new EmailAlreadyRegisteredException(request.Email);

        var hash = passwordHasher.Hash(request.Password);
        var user = User.Create(request.TenantId, request.Email, hash, request.DisplayName);

        await userRepository.AddAsync(user, ct);
        await userRepository.SaveChangesAsync(ct);

        return new RegisterResponse(user.Id, user.Email, user.TenantId);
    }
}

public sealed class EmailAlreadyRegisteredException(string email)
    : Exception($"Email is already registered: {email}");
