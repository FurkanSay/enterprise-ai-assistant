// Fixed-window rate limiter, keyed by tenant_id (falls back to client IP for
// anonymous endpoints like /auth/*).
using System.Threading.RateLimiting;

namespace Gateway.Api.Extensions;

public static class RateLimitExtensions
{
    public static void AddRateLimiting(this WebApplicationBuilder builder)
    {
        var permitPerWindow = builder.Configuration.GetValue<int>("RateLimit:PermitPerWindow");
        var windowSeconds = builder.Configuration.GetValue<int>("RateLimit:WindowSeconds");

        builder.Services.AddRateLimiter(options =>
        {
            options.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(ctx =>
            {
                var partitionKey = ctx.User.FindFirst("tenant_id")?.Value
                                   ?? ctx.Connection.RemoteIpAddress?.ToString()
                                   ?? "anon";

                return RateLimitPartition.GetFixedWindowLimiter(partitionKey, _ => new FixedWindowRateLimiterOptions
                {
                    PermitLimit = permitPerWindow,
                    Window = TimeSpan.FromSeconds(windowSeconds),
                    QueueLimit = 0
                });
            });

            options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
        });
    }
}
