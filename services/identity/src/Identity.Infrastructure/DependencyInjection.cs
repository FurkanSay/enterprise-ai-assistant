using Identity.Application.Abstractions;
using Identity.Infrastructure.Persistence;
using Identity.Infrastructure.Persistence.Repositories;
using Identity.Infrastructure.Services;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace Identity.Infrastructure;

public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        services.AddDbContext<IdentityDbContext>(opts =>
            opts.UseNpgsql(
                configuration.GetConnectionString("Postgres"),
                npg => npg.MigrationsHistoryTable("__ef_migrations", "identity_schema")));

        services.AddScoped<IUserRepository, UserRepository>();
        services.AddScoped<IRefreshTokenRepository, RefreshTokenRepository>();
        services.AddSingleton<IPasswordHasher, BCryptPasswordHasher>();

        services.Configure<JwtOptions>(configuration.GetSection("Jwt"));
        services.AddSingleton<ITokenService, JwtTokenService>();

        return services;
    }
}
