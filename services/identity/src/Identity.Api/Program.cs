using Identity.Api.Endpoints;
using Identity.Application;
using Identity.Infrastructure;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using Serilog;

var builder = WebApplication.CreateBuilder(args);

// ── Logging ────────────────────────────────────────────────────────────────
builder.Host.UseSerilog((ctx, _, cfg) =>
{
    cfg.ReadFrom.Configuration(ctx.Configuration)
       .Enrich.FromLogContext()
       .Enrich.WithProperty("Service", "identity")
       .WriteTo.Console(new Serilog.Formatting.Compact.CompactJsonFormatter());
});

// ── Clean Architecture wiring ──────────────────────────────────────────────
builder.Services.AddApplication();
builder.Services.AddInfrastructure(builder.Configuration);

// ── Telemetry ──────────────────────────────────────────────────────────────
builder.Services.AddOpenTelemetry()
    .ConfigureResource(r => r.AddService("identity", "0.1.0")
        .AddAttributes(new[] { new KeyValuePair<string, object>("service.namespace", "kai") }))
    .WithTracing(t => t
        .AddAspNetCoreInstrumentation(o => o.Filter = ctx => !ctx.Request.Path.StartsWithSegments("/health"))
        .AddEntityFrameworkCoreInstrumentation()
        .AddOtlpExporter(o => o.Endpoint = new Uri(
            builder.Configuration["OTEL_EXPORTER_OTLP_ENDPOINT"] ?? "http://otel-collector:4317")));

var app = builder.Build();

app.UseSerilogRequestLogging();

// Health
app.MapGet("/health/live", () => Results.Ok(new { status = "ok", service = "identity" }));
app.MapGet("/health/ready", () => Results.Ok(new { status = "ok", service = "identity" }));

// Domain endpoints
app.MapAuthEndpoints();

app.Run();

public partial class Program;
