import { buildProfile, parseJwtPayload } from './providers/claims';

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

export function guestProfile(): UserProfile {
  return { name: 'Guest', authType: 'guest' };
}

// Re-export claim helpers for backward compatibility
export {
  mergeClaims,
  parseJwtPayload,
  profileFromClaims,
  buildProfile,
  extractAvatarUrl,
  extractDisplayName,
  extractEmail,
  extractUserId,
  extractRoles,
} from './providers/claims';

export function profileFromToken(
  token: string,
  tokenParsed: Record<string, unknown> | null,
  authType: AuthType,
): UserProfile {
  const claims = tokenParsed ?? parseJwtPayload(token);
  return buildProfile(authType, claims, null);
}
