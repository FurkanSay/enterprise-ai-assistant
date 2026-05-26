using Identity.Domain.Common;

namespace Identity.Domain.Entities;

public sealed class Tenant : Entity
{
    public string Name { get; private set; } = string.Empty;
    public string Plan { get; private set; } = "free";  // free | pro | enterprise
    public bool IsActive { get; private set; } = true;

    // Quotas — kept inline; can extract as value object later if it grows.
    public int MaxDocuments { get; private set; } = 100;
    public long MaxTokensMonthly { get; private set; } = 1_000_000;
    public int MaxIterationsPerSession { get; private set; } = 50;
    public long MaxStorageBytes { get; private set; } = 10L * 1024 * 1024 * 1024;  // 10 GB

    private Tenant() { } // EF Core

    public static Tenant Create(string name, string plan = "free")
    {
        if (string.IsNullOrWhiteSpace(name))
            throw new ArgumentException("Tenant name is required.", nameof(name));

        return new Tenant { Name = name.Trim(), Plan = plan };
    }

    public void Deactivate()
    {
        IsActive = false;
        UpdatedAt = DateTime.UtcNow;
    }
}
