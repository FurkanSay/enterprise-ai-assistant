using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;

namespace Gateway.Api.Auth;

/// <summary>
/// JWT Bearer authentication — Identity service is the token issuer.
/// Gateway only VALIDATES tokens (issuer + audience + lifetime + signature).
///
/// Algorithm choice: HS256 with a shared secret between Identity and
/// Gateway. We do NOT use the OpenID-Connect discovery endpoint
/// (`Authority`) because the Identity service does not expose a JWKS —
/// only Identity ever signs tokens, so we configure the same symmetric
/// key on both sides via the JWT__SIGNING_KEY env var.
///
/// Production hardening path (not in scope here): RS256 + Identity
/// exposes /.well-known/jwks.json, Gateway sets `Authority`, key rotation
/// becomes a Kubernetes Secret swap.
/// </summary>
public static class AuthExtensions
{
    public static IServiceCollection AddGatewayAuth(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        var jwt = configuration.GetSection("Jwt");
        var signingKey = jwt["SigningKey"]
            ?? throw new InvalidOperationException(
                "Jwt:SigningKey is not configured. " +
                "Provide via env var Jwt__SigningKey or appsettings.json.");

        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
            .AddJwtBearer(options =>
            {
                options.RequireHttpsMetadata = false; // intra-cluster HTTP

                options.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuer = true,
                    ValidIssuer = jwt["Issuer"],
                    ValidateAudience = true,
                    ValidAudience = jwt["Audience"],
                    ValidateLifetime = true,
                    ValidateIssuerSigningKey = true,
                    IssuerSigningKey = new SymmetricSecurityKey(
                        Encoding.UTF8.GetBytes(signingKey)),
                    ClockSkew = TimeSpan.FromSeconds(30),
                };
            });

        services.AddAuthorization(options =>
        {
            // Default policy: require an authenticated identity. YARP routes
            // tagged AuthorizationPolicy: "default" will use this — anonymous
            // routes (login, register) leave the tag off.
            options.DefaultPolicy = new Microsoft.AspNetCore.Authorization
                .AuthorizationPolicyBuilder()
                .RequireAuthenticatedUser()
                .Build();
        });
        return services;
    }
}