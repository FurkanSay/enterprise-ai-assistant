using Identity.Domain.Entities;

namespace Identity.Application.Abstractions;

public sealed record TokenPair(
    string AccessToken,
    DateTime AccessTokenExpiresAt,
    string RefreshToken,
    DateTime RefreshTokenExpiresAt);

public interface ITokenService
{
    TokenPair Issue(User user, IEnumerable<string> roles);
}
