using Identity.Domain.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace Identity.Infrastructure.Persistence.Configurations;

internal sealed class RoleConfiguration : IEntityTypeConfiguration<Role>
{
    public void Configure(EntityTypeBuilder<Role> builder)
    {
        builder.ToTable("roles");
        builder.HasKey(x => x.Id);

        builder.Property(x => x.Id).HasColumnName("id");
        // tenant_id is on the table but not yet on the domain Role entity.
        // We do not expose it on the aggregate because role lookups happen
        // through a tenant-scoped query (RLS does the rest).
        builder.Property(x => x.Name).HasColumnName("name").IsRequired().HasMaxLength(60);
        builder.Property(x => x.Description).HasColumnName("description").HasMaxLength(255);
        builder.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();

        builder.Ignore(x => x.UpdatedAt);
    }
}
