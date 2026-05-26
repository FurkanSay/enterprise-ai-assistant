// Translate JWT claims → X-Tenant-Id / X-User-Id / X-Roles headers on every
// proxied request. Downstream services trust these headers (they only reach
// the service through the gateway, internal network is segmented).
namespace Gateway.Api.Extensions;

public static class TenantForwardingExtensions
{
    public static void AddTenantContextForwarding(this WebApplicationBuilder builder)
    {
        // Nothing to register — middleware below uses HttpContext directly.
    }

    public static void UseTenantContextForwarding(this WebApplication app)
    {
        app.Use(async (ctx, next) =>
        {
            if (ctx.User.Identity?.IsAuthenticated == true)
            {
                var tenantId = ctx.User.FindFirst("tenant_id")?.Value;
                var userId = ctx.User.FindFirst("sub")?.Value
                              ?? ctx.User.FindFirst("user_id")?.Value;
                var roles = ctx.User.FindAll("role").Select(c => c.Value);

                if (tenantId is not null) ctx.Request.Headers["X-Tenant-Id"] = tenantId;
                if (userId is not null)   ctx.Request.Headers["X-User-Id"] = userId;
                ctx.Request.Headers["X-Roles"] = string.Join(",", roles);
                ctx.Request.Headers["X-Caller-Service"] = "gateway";
            }
            await next();
        });
    }
}
