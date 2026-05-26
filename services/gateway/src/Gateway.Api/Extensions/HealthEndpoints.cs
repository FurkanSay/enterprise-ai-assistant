// Split liveness vs readiness so K8s can probe them independently.
namespace Gateway.Api.Extensions;

public static class HealthEndpoints
{
    public static void MapHealthEndpoints(this WebApplication app)
    {
        app.MapGet("/health/live", () => Results.Ok(new { status = "ok", service = "gateway" }))
            .AllowAnonymous();

        app.MapGet("/health/ready", () => Results.Ok(new { status = "ok", service = "gateway" }))
            .AllowAnonymous();
    }
}
