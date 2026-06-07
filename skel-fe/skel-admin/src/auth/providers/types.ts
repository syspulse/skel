import type { AuthType, UserProfile } from '../userProfile';

/** Normalized auth session returned by every provider adapter. */
export interface AuthSession {
  token: string | null;
  tokenParsed: Record<string, unknown> | null;
  userInfo: Record<string, unknown> | null;
  user: UserProfile;
}

/** Pluggable auth backend — Keycloak, Google, Skel, Guest. */
export interface AuthProviderAdapter {
  readonly authType: AuthType;

  /** Build session from current provider state (fetch remote profile if supported). */
  refreshSession(): Promise<AuthSession | null>;

  /** Start interactive login (redirect or in-app). */
  login(): void;

  /** End session. */
  logout(): void;
}
