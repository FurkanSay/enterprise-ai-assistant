using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;

namespace Gateway.Api.Auth;

/// <summary>
/// JWT Bearer authentication — Identity service is the token issuer.
/// Gateway only VALIDATES tokens (RS256 + issuer + audience + lifetime).
/// </summary>
public static class AuthExtensions
{
    public static IServiceCollection AddGatewayAuth(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        var jwt = configuration.GetSection("Jwt");

        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
            .AddJwtBearer(options =>
            {
                options.Authority = jwt["Authority"];
                options.RequireHttpsMetadata = !string.Equals(
                    configuration["ASPNETCORE_ENVIRONMENT"],
                    "Development",
                    StringComparison.OrdinalIgnoreCase);

                options.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuer = true,
                    ValidIssuer = jwt["Issuer"],
                    ValidateAudience = true,
                    ValidAudience = jwt["Audience"],
                    ValidateLifetime = true,
                    ValidateIssuerSigningKey = true,
                    ClockSkew = TimeSpan.FromSeconds(30),
                };
            });

        services.AddAuthorization();
        return services;
    }
}
