namespace Gateway.Api.HealthChecks;

/// <summary>
/// Split health endpoints — Kubernetes-friendly:
///   /health/live   → process responding
///   /health/ready  → dependencies (downstream services) reachable
/// </summary>
public static class HealthEndpointsExtensions
{
    public static IServiceCollection AddGatewayHealthChecks(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        // TODO: register IHealthCheck implementations for downstream services
        services.AddHealthChecks();
        return services;
    }

    public static WebApplication MapHealthEndpoints(this WebApplication app)
    {
        app.MapGet("/health/live", () => Results.Ok(new
        {
            status = "ok",
            service = "gateway",
        }));

        app.MapGet("/health/ready", () => Results.Ok(new
        {
            status = "ok",
            service = "gateway",
            checks = new
            {
                identity = "TODO",
                documents = "TODO",
                aiengine = "TODO",
            },
        }));

        return app;
    }
}
