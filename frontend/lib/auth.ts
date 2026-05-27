/**
 * Browser-side auth state — access token + refresh token + decoded JWT
 * claims in localStorage.
 *
 * Trade-offs:
 *   - localStorage is XSS-readable. Acceptable for this stage because the
 *     project is a demo/portfolio. A production cut would migrate to
 *     httpOnly cookies for the refresh token.
 *   - Access tokens live 15min (per Identity's JwtOptions). When a 401
 *     comes back the api-client swaps the refresh token at
 *     /api/v1/auth/refresh and retries the original request once — see
 *     `authedFetch` in api-client.ts. Rotation is single-use: every
 *     refresh revokes the prior token and mints a new one.
 */

const ACCESS_KEY = 'kai.access';
const REFRESH_KEY = 'kai.refresh';
const USER_KEY = 'kai.user';

export interface AuthUser {
  userId: string;
  tenantId: string;
  email: string;
  roles: string[];
}

export interface LoginResponse {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
}

export function saveSession(loginResponse: LoginResponse): AuthUser {
  if (typeof window === 'undefined') {
    throw new Error('saveSession called on server');
  }
  const user = decodeUserFromJwt(loginResponse.accessToken);
  localStorage.setItem(ACCESS_KEY, loginResponse.accessToken);
  localStorage.setItem(REFRESH_KEY, loginResponse.refreshToken);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  return user;
}

/** Replace just the tokens after a refresh — keep the same user info. */
export function updateTokens(accessToken: string, refreshToken: string): void {
  if (typeof window === 'undefined') return;
  localStorage.setItem(ACCESS_KEY, accessToken);
  localStorage.setItem(REFRESH_KEY, refreshToken);
}

export function getAccessToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(ACCESS_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(REFRESH_KEY);
}

export function getCurrentUser(): AuthUser | null {
  if (typeof window === 'undefined') return null;
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    return null;
  }
}

export function clearSession(): void {
  if (typeof window === 'undefined') return;
  localStorage.removeItem(ACCESS_KEY);
  localStorage.removeItem(REFRESH_KEY);
  localStorage.removeItem(USER_KEY);
}

/**
 * Decode the JWT payload WITHOUT verifying the signature. We only do this
 * to display user info — the server is still the only thing that trusts
 * the claims. Never use this output for authorization decisions.
 */
function decodeUserFromJwt(token: string): AuthUser {
  const parts = token.split('.');
  if (parts.length !== 3) {
    throw new Error('Invalid JWT — expected three dot-separated parts');
  }
  const payload = JSON.parse(
    atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')),
  );
  // Identity issues claims as: sub, email, tenant_id, roles (comma-separated
  // or duplicated), jti, nbf, exp, iss, aud.
  const rolesRaw = payload.roles;
  let roles: string[] = [];
  if (Array.isArray(rolesRaw)) {
    roles = rolesRaw.map(String);
  } else if (typeof rolesRaw === 'string') {
    roles = rolesRaw.split(',').filter(Boolean);
  }
  return {
    userId: String(payload.sub ?? ''),
    tenantId: String(payload.tenant_id ?? ''),
    email: String(payload.email ?? ''),
    roles,
  };
}
