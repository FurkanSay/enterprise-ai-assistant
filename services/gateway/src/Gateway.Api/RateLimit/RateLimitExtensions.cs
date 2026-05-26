using System.Threading.RateLimiting;

namespace Gateway.Api.RateLimit;

/// <summary>
/// Per-tenant + per-IP rate limiting.
/// Currently uses in-memory partitions; for multi-instance Gateway,
/// swap to a Redis-backed limiter (StackExchange.Redis already in deps).
/// </summary>
public static class RateLimitExtensions
{
    public static IServiceCollection AddGatewayRateLimiter(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        var perMinute = configuration.GetValue<int?>("RateLimit:PerTenantPerMinute") ?? 120;

        services.AddRateLimiter(options =>
        {
            options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;

            // Per-tenant bucket — falls back to IP if no tenant header (anonymous)
            options.GlobalLimiter = PartitionedRateLimiter.Create<HttpContext, string>(ctx =>
            {
                var partitionKey = ctx.User.FindFirst("tenant_id")?.Value
                                   ?? ctx.Connection.RemoteIpAddress?.ToString()
                                   ?? "anonymous";

                return RateLimitPartition.GetFixedWindowLimiter(partitionKey, _ =>
                    new FixedWindowRateLimiterOptions
                    {
                        PermitLimit = perMinute,
                        Window = TimeSpan.FromMinutes(1),
                        QueueProcessingOrder = QueueProcessingOrder.OldestFirst,
                        QueueLimit = 0,
                        AutoReplenishment = true,
                    });
            });
        });

        return services;
    }
}
