using Identity.Domain.Entities;

namespace Identity.Application.Abstractions;

public interface IRefreshTokenRepository
{
    Task AddAsync(RefreshToken token, CancellationToken ct = default);

    /// <summary>
    /// Look up a refresh token by its SHA-256 hash, ignoring expiry +
    /// revocation. The caller decides what to do with revoked/expired
    /// rows (e.g. a request that hits a revoked row is still useful
    /// signal — it likely means a replayed stolen token).
    /// </summary>
    Task<RefreshToken?> FindByHashAsync(string tokenHash, CancellationToken ct = default);

    Task<int> SaveChangesAsync(CancellationToken ct = default);
}
