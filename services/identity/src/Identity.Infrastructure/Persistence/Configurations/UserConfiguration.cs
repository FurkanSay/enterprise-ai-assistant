using Identity.Domain.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace Identity.Infrastructure.Persistence.Configurations;

/// <summary>
/// EF Core defaults to PascalCase column names; the Postgres init script
/// uses snake_case. Map every column explicitly so EF emits the right SQL.
/// Also tells EF to ignore the entity-base UpdatedAt (the table has no
/// such column — we're only persisting a subset of the aggregate).
/// </summary>
internal sealed class UserConfiguration : IEntityTypeConfiguration<User>
{
    public void Configure(EntityTypeBuilder<User> builder)
    {
        builder.ToTable("users");
        builder.HasKey(x => x.Id);

        builder.Property(x => x.Id).HasColumnName("id");
        builder.Property(x => x.TenantId).HasColumnName("tenant_id").IsRequired();
        builder.Property(x => x.Email).HasColumnName("email").IsRequired().HasMaxLength(320);
        builder.Property(x => x.PasswordHash).HasColumnName("password_hash").IsRequired().HasMaxLength(255);
        builder.Property(x => x.DisplayName).HasColumnName("display_name").HasMaxLength(255);
        builder.Property(x => x.IsActive).HasColumnName("is_active").IsRequired();
        builder.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();

        builder.Ignore(x => x.UpdatedAt);
        // The in-memory _roleIds collection lives in user_roles — see the
        // separate join-table query path. EF doesn't need to know.
        builder.Ignore(x => x.RoleIds);

        builder.HasIndex(x => new { x.TenantId, x.Email }).IsUnique();
        builder.HasIndex(x => x.TenantId);
    }
}
