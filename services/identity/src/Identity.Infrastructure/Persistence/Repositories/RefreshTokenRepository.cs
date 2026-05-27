using Identity.Application.Abstractions;
using Identity.Domain.Entities;
using Microsoft.EntityFrameworkCore;

namespace Identity.Infrastructure.Persistence.Repositories;

internal sealed class RefreshTokenRepository(IdentityDbContext db) : IRefreshTokenRepository
{
    public Task AddAsync(RefreshToken token, CancellationToken ct = default) =>
        db.RefreshTokens.AddAsync(token, ct).AsTask();

    public Task<RefreshToken?> FindByHashAsync(string tokenHash, CancellationToken ct = default) =>
        db.RefreshTokens.FirstOrDefaultAsync(t => t.TokenHash == tokenHash, ct);

    public Task<int> SaveChangesAsync(CancellationToken ct = default) =>
        db.SaveChangesAsync(ct);
}
