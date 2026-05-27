using Identity.Domain.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace Identity.Infrastructure.Persistence.Configurations;

internal sealed class TenantConfiguration : IEntityTypeConfiguration<Tenant>
{
    public void Configure(EntityTypeBuilder<Tenant> builder)
    {
        builder.ToTable("tenants");
        builder.HasKey(x => x.Id);

        builder.Property(x => x.Id).HasColumnName("id");
        builder.Property(x => x.Name).HasColumnName("name").IsRequired().HasMaxLength(120);
        builder.Property(x => x.Plan).HasColumnName("plan").IsRequired().HasMaxLength(40);
        builder.Property(x => x.IsActive).HasColumnName("is_active").IsRequired();
        builder.Property(x => x.CreatedAt).HasColumnName("created_at").IsRequired();

        // The quota columns are not yet on the table — they are domain-side
        // until the schema catches up. Ignore them so EF doesn't expect SQL.
        builder.Ignore(x => x.MaxDocuments);
        builder.Ignore(x => x.MaxTokensMonthly);
        builder.Ignore(x => x.MaxIterationsPerSession);
        builder.Ignore(x => x.MaxStorageBytes);

        builder.Ignore(x => x.UpdatedAt);
    }
}
