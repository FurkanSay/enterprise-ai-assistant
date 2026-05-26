using Identity.Domain.Common;

namespace Identity.Domain.Entities;

public sealed class Role : Entity
{
    public string Name { get; private set; } = string.Empty;
    public string Description { get; private set; } = string.Empty;

    private Role() { }

    public static Role Create(string name, string description = "")
    {
        if (string.IsNullOrWhiteSpace(name))
            throw new ArgumentException("Role name is required.", nameof(name));

        return new Role { Name = name.Trim().ToLowerInvariant(), Description = description };
    }
}
