using Identity.Application.Abstractions;
using MediatR;

namespace Identity.Application.Auth.Me;

/// <summary>
/// Returns the currently-authenticated user. Identity service trusts the
/// X-User-Id and X-Tenant-Id headers injected by Gateway after JWT
/// validation — same trust boundary as the other downstream services
/// (Documents, AI Engine).
/// </summary>
public sealed record MeQuery(Guid UserId, Guid TenantId)
    : IRequest<MeResponse>;

public sealed record MeResponse(
    Guid UserId,
    Guid TenantId,
    string Email,
    string DisplayName,
    bool IsActive);

internal sealed class MeQueryHandler(IUserRepository userRepository)
    : IRequestHandler<MeQuery, MeResponse>
{
    public async Task<MeResponse> Handle(MeQuery request, CancellationToken ct)
    {
        var user = await userRepository.FindByIdAsync(request.UserId, ct)
                   ?? throw new UserNotFoundException(request.UserId);

        // Tenant cross-check — claim says one thing, DB says another, fail
        // closed. A stolen access token cannot cross tenants.
        if (user.TenantId != request.TenantId)
            throw new UserNotFoundException(request.UserId);

        return new MeResponse(
            user.Id,
            user.TenantId,
            user.Email,
            user.DisplayName,
            user.IsActive);
    }
}

public sealed class UserNotFoundException(Guid userId)
    : Exception($"User not found: {userId}");
