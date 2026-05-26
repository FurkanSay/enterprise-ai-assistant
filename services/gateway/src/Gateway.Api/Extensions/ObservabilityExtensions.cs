// Serilog (structured JSON) + OpenTelemetry (traces, metrics).
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using Serilog;

namespace Gateway.Api.Extensions;

public static class ObservabilityExtensions
{
    public static void AddObservability(this WebApplicationBuilder builder)
    {
        // Serilog — JSON to stdout, picks up tenant_id from log context
        builder.Host.UseSerilog((ctx, lc) => lc
            .ReadFrom.Configuration(ctx.Configuration)
            .Enrich.FromLogContext());

        var otelEndpoint = builder.Configuration["Otel:Endpoint"]
                            ?? "http://localhost:4317";

        builder.Services
            .AddOpenTelemetry()
            .ConfigureResource(r => r
                .AddService("gateway", serviceVersion: "0.1.0")
                .AddAttributes(new Dictionary<string, object> { ["deployment.environment"] = builder.Environment.EnvironmentName }))
            .WithTracing(t => t
                .AddAspNetCoreInstrumentation()
                .AddHttpClientInstrumentation()
                .AddOtlpExporter(opt => opt.Endpoint = new Uri(otelEndpoint)));
    }
}
