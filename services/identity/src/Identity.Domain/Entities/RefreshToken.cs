using System.Security.Cryptography;
using System.Text;
using Identity.Domain.Common;

namespace Identity.Domain.Entities;

/// <summary>
/// Persisted refresh-token row. We store SHA-256 of the raw token, not
/// the token itself — a DB leak then exposes only hashes (and the input
/// space is 64 random bytes, so brute-force is infeasible).
///
/// Lifecycle:
///   issued        → row inserted with RevokedAt = NULL
///   used (rotate) → RevokedAt = NOW(), new row inserted for the
///                   replacement token
///   logout        → RevokedAt = NOW()
///   replayed      → lookup of an already-revoked hash → 401 (and in a
///                   stricter setup, revoke ALL tokens for the user as
///                   it likely indicates theft)
/// </summary>
public sealed class RefreshToken : Entity
{
    public Guid TenantId { get; private set; }
    public Guid UserId { get; private set; }
    public string TokenHash { get; private set; } = string.Empty;
    public DateTime ExpiresAt { get; private set; }
    public DateTime? RevokedAt { get; private set; }

    private RefreshToken() { }

    /// <summary>Hash a plaintext token with SHA-256 → lowercase hex.</summary>
    public static string Hash(string plaintext)
    {
        if (string.IsNullOrEmpty(plaintext))
            throw new ArgumentException("Token must be non-empty.", nameof(plaintext));
        var bytes = SHA256.HashData(Encoding.UTF8.GetBytes(plaintext));
        return Convert.ToHexString(bytes).ToLowerInvariant();
    }

    public static RefreshToken Issue(
        Guid tenantId,
        Guid userId,
        string plaintextToken,
        DateTime expiresAt)
    {
        if (tenantId == Guid.Empty)
            throw new ArgumentException("TenantId is required.", nameof(tenantId));
        if (userId == Guid.Empty)
            throw new ArgumentException("UserId is required.", nameof(userId));

        return new RefreshToken
        {
            TenantId = tenantId,
            UserId = userId,
            TokenHash = Hash(plaintextToken),
            ExpiresAt = expiresAt,
        };
    }

    public void Revoke()
    {
        // Idempotent — re-revoking is a no-op so we don't accidentally
        // overwrite the original revocation timestamp on a double call.
        RevokedAt ??= DateTime.UtcNow;
    }

    public bool IsActive(DateTime now) => RevokedAt is null && ExpiresAt > now;
}
