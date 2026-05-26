// JWT bearer auth registration. The Authority points at Identity service which
// publishes JWKS at /.well-known/jwks.json. Gateway does not validate signatures
// itself for every request — it lets ASP.NET Core JwtBearer middleware do it.
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.IdentityModel.Tokens;

namespace Gateway.Api.Extensions;

public static class AuthExtensions
{
    public static void AddAuthentication(this WebApplicationBuilder builder)
    {
        var jwt = builder.Configuration.GetSection("Jwt");

        builder.Services
            .AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
            .AddJwtBearer(options =>
            {
                options.Authority = jwt["Authority"];
                options.Audience = jwt["Audience"];
                options.RequireHttpsMetadata = jwt.GetValue<bool>("RequireHttpsMetadata");
                options.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuer = true,
                    ValidIssuer = jwt["Issuer"],
                    ValidateAudience = true,
                    ValidateLifetime = true,
                    ClockSkew = TimeSpan.FromSeconds(30)
                };
            });

        builder.Services.AddAuthorizationBuilder()
            .AddPolicy("AuthenticatedUser", p => p.RequireAuthenticatedUser());
    }
}
