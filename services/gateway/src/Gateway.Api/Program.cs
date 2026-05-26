using Gateway.Api.Auth;
using Gateway.Api.HealthChecks;
using Gateway.Api.RateLimit;
using Gateway.Api.Telemetry;
using Serilog;

var builder = WebApplication.CreateBuilder(args);

// ── Logging (Serilog, structured JSON) ─────────────────────────────────────
builder.Host.UseSerilog((context, services, configuration) =>
{
    configuration
        .ReadFrom.Configuration(context.Configuration)
        .Enrich.FromLogContext()
        .Enrich.WithProperty("Service", "gateway")
        .WriteTo.Console(new Serilog.Formatting.Compact.CompactJsonFormatter());
});

// ── Auth (JWT validation — Identity issues the tokens) ─────────────────────
builder.Services.AddGatewayAuth(builder.Configuration);

// ── Reverse proxy (YARP — config-driven routes) ────────────────────────────
builder.Services.AddReverseProxy()
    .LoadFromConfig(builder.Configuration.GetSection("ReverseProxy"));

// ── Rate limiting (built-in .NET 10, Redis-backed for multi-instance) ──────
builder.Services.AddGatewayRateLimiter(builder.Configuration);

// ── Telemetry (OpenTelemetry → OTel collector) ─────────────────────────────
builder.Services.AddGatewayTelemetry(builder.Configuration);

// ── Health checks ──────────────────────────────────────────────────────────
builder.Services.AddGatewayHealthChecks(builder.Configuration);

// ── CORS (browser frontend) ────────────────────────────────────────────────
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy =>
    {
        var origins = builder.Configuration.GetSection("Cors:AllowedOrigins").Get<string[]>()
                      ?? Array.Empty<string>();
        policy.WithOrigins(origins)
              .AllowAnyHeader()
              .AllowAnyMethod()
              .AllowCredentials();
    });
});

var app = builder.Build();

// ── Pipeline ───────────────────────────────────────────────────────────────
app.UseSerilogRequestLogging();
app.UseCors();
app.UseAuthentication();
app.UseAuthorization();
app.UseRateLimiter();

// Inject tenant context headers downstream after JWT validation
app.UseTenantContextForwarding();

// Health endpoints — NOT proxied (gateway is the responder)
app.MapHealthEndpoints();

// YARP — all routes from appsettings.json (Documents, Identity, AI Engine, etc.)
app.MapReverseProxy();

app.Run();

// Expose for integration tests
public partial class Program;
