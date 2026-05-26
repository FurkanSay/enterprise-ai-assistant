using OpenTelemetry.Resources;
using OpenTelemetry.Trace;

namespace Gateway.Api.Telemetry;

/// <summary>
/// OpenTelemetry tracing — Gateway is the entry point of distributed traces.
/// Every request creates a root span; downstream services continue it.
/// </summary>
public static class TelemetryExtensions
{
    public static IServiceCollection AddGatewayTelemetry(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        var otlpEndpoint = configuration["OTEL_EXPORTER_OTLP_ENDPOINT"]
                           ?? "http://otel-collector:4317";

        services.AddOpenTelemetry()
            .ConfigureResource(resource => resource
                .AddService(
                    serviceName: "gateway",
                    serviceVersion: "0.1.0")
                .AddAttributes(new[]
                {
                    new KeyValuePair<string, object>("service.namespace", "kai"),
                    new KeyValuePair<string, object>(
                        "deployment.environment",
                        configuration["ASPNETCORE_ENVIRONMENT"] ?? "development"),
                }))
            .WithTracing(tracing => tracing
                .AddAspNetCoreInstrumentation(opts =>
                {
                    // Skip health probes from traces
                    opts.Filter = context => !context.Request.Path.StartsWithSegments("/health");
                })
                .AddHttpClientInstrumentation()
                .AddOtlpExporter(opts =>
                {
                    opts.Endpoint = new Uri(otlpEndpoint);
                }));

        return services;
    }
}
