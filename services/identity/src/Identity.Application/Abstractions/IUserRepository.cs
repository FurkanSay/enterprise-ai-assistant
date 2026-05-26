using Identity.Domain.Entities;

namespace Identity.Application.Abstractions;

/// <summary>
/// Application defines WHAT it needs; Infrastructure provides the HOW.
/// This inversion (DIP) is the core of Clean Architecture.
/// </summary>
public interface IUserRepository
{
    Task<User?> FindByEmailAsync(string email, CancellationToken ct = default);
    Task<User?> FindByIdAsync(Guid id, CancellationToken ct = default);
    Task AddAsync(User user, CancellationToken ct = default);
    Task<int> SaveChangesAsync(CancellationToken ct = default);
}
