using System.Security.Claims;

namespace Gateway.Api.Auth;

/// <summary>
/// After JWT validation, Gateway is the SINGLE authority on tenant identity.
/// We extract claims and forward them as X-* headers to downstream services.
/// Downstream services trust these headers — they do NOT re-validate JWT.
/// </summary>
public static class TenantContextForwardingExtensions
{
    public const string TenantHeader = "X-Tenant-Id";
    public const string UserHeader = "X-User-Id";
    public const string RolesHeader = "X-User-Roles";
    public const string TraceHeader = "X-Trace-Id";
    public const string CorrelationHeader = "X-Correlation-Id";
    public const string CallerHeader = "X-Caller-Service";

    private const string TenantClaim = "tenant_id";
    private const string RolesClaim = "roles";

    public static IApplicationBuilder UseTenantContextForwarding(this IApplicationBuilder app)
    {
        return app.Use(async (context, next) =>
        {
            var user = context.User;
            if (user.Identity?.IsAuthenticated == true)
            {
                var tenantId = user.FindFirstValue(TenantClaim);
                var userId = user.FindFirstValue(ClaimTypes.NameIdentifier) ?? user.FindFirstValue("sub");
                var roles = string.Join(',', user.FindAll(RolesClaim).Select(c => c.Value));

                if (!string.IsNullOrEmpty(tenantId))
                {
                    context.Request.Headers[TenantHeader] = tenantId;
                }

                if (!string.IsNullOrEmpty(userId))
                {
                    context.Request.Headers[UserHeader] = userId;
                }

                if (!string.IsNullOrEmpty(roles))
                {
                    context.Request.Headers[RolesHeader] = roles;
                }

                context.Request.Headers[CallerHeader] = "gateway";

                // Trace id from current activity (OTel sets this)
                var activity = System.Diagnostics.Activity.Current;
                if (activity is not null)
                {
                    context.Request.Headers[TraceHeader] = activity.TraceId.ToString();
                }
            }

            await next();
        });
    }
}
