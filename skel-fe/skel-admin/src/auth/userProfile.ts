/** Authentication provider identifier. Extend when adding new auth backends. */
export type AuthType = 'guest' | 'keycloak' | 'skel' | 'google';

export interface UserProfile {
  id?: string;
  name: string;
  email?: string;
  avatarUrl?: string;
  roles?: string[];
  authType: AuthType;
}

const AVATAR_CLAIMS = ['picture', 'avatar', 'avatar_url', 'profile_image_url', 'photo'] as const;

/** Decode JWT payload (middle segment) without verifying signature. */
export function parseJwtPayload(
  token: string | null | undefined,
): Record<string, unknown> | null {
  if (!token) return null;
  const parts = token.split('.');
  if (parts.length < 2) return null;
  try {
    const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    return JSON.parse(atob(padded)) as Record<string, unknown>;
  } catch {
    return null;
  }
}

function strClaim(claims: Record<string, unknown>, key: string): string | undefined {
  const v = claims[key];
  return typeof v === 'string' && v.length > 0 ? v : undefined;
}

export function extractAvatarUrl(claims: Record<string, unknown>): string | undefined {
  for (const key of AVATAR_CLAIMS) {
    const url = strClaim(claims, key);
    if (url) return url;
  }
  return undefined;
}

export function extractDisplayName(claims: Record<string, unknown>): string {
  const name = strClaim(claims, 'name');
  if (name) return name;

  const preferred = strClaim(claims, 'preferred_username');
  if (preferred) return preferred;

  const given = strClaim(claims, 'given_name');
  const family = strClaim(claims, 'family_name');
  if (given || family) return [given, family].filter(Boolean).join(' ');

  const username = strClaim(claims, 'username');
  if (username) return username;

  const sub = strClaim(claims, 'sub');
  if (sub) return sub;

  return 'User';
}

export function extractUserId(claims: Record<string, unknown>): string | undefined {
  return strClaim(claims, 'sub') ?? strClaim(claims, 'user_id') ?? strClaim(claims, 'id');
}

export function extractRoles(claims: Record<string, unknown>): string[] | undefined {
  const realmAccess = claims.realm_access as { roles?: string[] } | undefined;
  if (realmAccess?.roles?.length) return realmAccess.roles;

  const roles = claims.roles;
  if (Array.isArray(roles) && roles.every((r) => typeof r === 'string')) {
    return roles as string[];
  }
  return undefined;
}

/** Build a UserProfile from JWT claims and auth provider. */
export function profileFromClaims(
  claims: Record<string, unknown> | null,
  authType: AuthType,
): UserProfile {
  if (!claims) {
    return { name: authType === 'guest' ? 'Guest' : 'User', authType };
  }
  return {
    id: extractUserId(claims),
    name: extractDisplayName(claims),
    email: strClaim(claims, 'email'),
    avatarUrl: extractAvatarUrl(claims),
    roles: extractRoles(claims),
    authType,
  };
}

export function guestProfile(): UserProfile {
  return { name: 'Guest', authType: 'guest' };
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function profileFromKeycloak(kc: any): UserProfile {
  const claims = (kc.tokenParsed ?? null) as Record<string, unknown> | null;
  return profileFromClaims(claims, 'keycloak');
}

export function profileFromToken(
  token: string,
  tokenParsed: Record<string, unknown> | null,
  authType: AuthType,
): UserProfile {
  const claims = tokenParsed ?? parseJwtPayload(token);
  return profileFromClaims(claims, authType);
}
