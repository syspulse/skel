import type { AuthType, UserProfile } from '../userProfile';

const AVATAR_CLAIMS = ['picture', 'avatar', 'avatar_url', 'profile_image_url', 'photo'] as const;

const EMAIL_CLAIMS = ['email', 'upn', 'preferred_username'] as const;

/** Merge claim maps; later maps override earlier ones (userinfo over JWT). */
export function mergeClaims(
  ...sources: (Record<string, unknown> | null | undefined)[]
): Record<string, unknown> | null {
  const merged: Record<string, unknown> = {};
  let hasAny = false;
  for (const src of sources) {
    if (!src) continue;
    Object.assign(merged, src);
    hasAny = true;
  }
  return hasAny ? merged : null;
}

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

function looksLikeEmail(value: string): boolean {
  return value.includes('@');
}

export function extractAvatarUrl(claims: Record<string, unknown>): string | undefined {
  for (const key of AVATAR_CLAIMS) {
    const url = strClaim(claims, key);
    if (url) return url;
  }
  return undefined;
}

export function extractEmail(claims: Record<string, unknown>): string | undefined {
  for (const key of EMAIL_CLAIMS) {
    const value = strClaim(claims, key);
    if (value && (key === 'email' || looksLikeEmail(value))) return value;
  }
  return undefined;
}

export function extractDisplayName(claims: Record<string, unknown>): string {
  const name = strClaim(claims, 'name');
  if (name) return name;

  const preferred = strClaim(claims, 'preferred_username');
  if (preferred && !looksLikeEmail(preferred)) return preferred;

  const given = strClaim(claims, 'given_name');
  const family = strClaim(claims, 'family_name');
  if (given || family) return [given, family].filter(Boolean).join(' ');

  const upn = strClaim(claims, 'upn');
  if (upn) return upn.includes('@') ? upn.split('@')[0] : upn;

  const email = extractEmail(claims);
  if (email?.includes('@')) return email.split('@')[0];

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
  const groups = claims.groups;
  if (Array.isArray(groups) && groups.every((g) => typeof g === 'string')) {
    return groups as string[];
  }

  const realmAccess = claims.realm_access as { roles?: string[] } | undefined;
  if (realmAccess?.roles?.length) return realmAccess.roles;

  const roles = claims.roles;
  if (Array.isArray(roles) && roles.every((r) => typeof r === 'string')) {
    return roles as string[];
  }
  return undefined;
}

/** Build UserProfile from merged JWT + UserInfo (or any claim sources). */
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
    email: extractEmail(claims),
    avatarUrl: extractAvatarUrl(claims),
    roles: extractRoles(claims),
    authType,
  };
}

export function buildProfile(
  authType: AuthType,
  tokenParsed: Record<string, unknown> | null,
  userInfo: Record<string, unknown> | null,
): UserProfile {
  return profileFromClaims(mergeClaims(tokenParsed, userInfo), authType);
}
