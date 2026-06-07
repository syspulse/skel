import type { AuthType } from '../userProfile';
import { buildProfile } from './claims';
import type { AuthProviderAdapter, AuthSession } from './types';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type KeycloakInstance = any;

function getKeycloakConfig() {
  return {
    url:
      localStorage.getItem('VITE_KEYCLOAK_URL') ||
      import.meta.env.VITE_KEYCLOAK_URL ||
      'http://localhost:8180',
    realm:
      localStorage.getItem('VITE_KEYCLOAK_REALM') ||
      import.meta.env.VITE_KEYCLOAK_REALM ||
      'master',
    clientId:
      localStorage.getItem('VITE_KEYCLOAK_CLIENT_ID') ||
      import.meta.env.VITE_KEYCLOAK_CLIENT_ID ||
      'skel-admin',
  };
}

let keycloakSingleton: KeycloakInstance | null = null;
let keycloakInitPromise: Promise<KeycloakInstance> | null = null;

/** Shared Keycloak client singleton (StrictMode-safe). */
export async function getKeycloakInstance(): Promise<KeycloakInstance> {
  if (keycloakSingleton) return keycloakSingleton;
  if (keycloakInitPromise) return keycloakInitPromise;

  keycloakInitPromise = (async () => {
    const KeycloakModule = await import('keycloak-js');
    const Keycloak = KeycloakModule.default;
    const kc: KeycloakInstance = new Keycloak(getKeycloakConfig());
    await kc.init({ checkLoginIframe: false });
    keycloakSingleton = kc;
    return kc;
  })().catch((err) => {
    keycloakInitPromise = null;
    throw err;
  });

  return keycloakInitPromise;
}

export function getKeycloakRef(): KeycloakInstance | null {
  return keycloakSingleton;
}

async function fetchUserInfo(kc: KeycloakInstance): Promise<Record<string, unknown> | null> {
  if (!kc?.authenticated || !kc.token) return null;
  try {
    const info = await kc.loadUserInfo();
    return (info ?? null) as Record<string, unknown> | null;
  } catch {
    return null;
  }
}

function sessionFromKeycloak(
  kc: KeycloakInstance,
  authType: AuthType,
  userInfo: Record<string, unknown> | null,
): AuthSession {
  const tokenParsed = (kc.tokenParsed as Record<string, unknown> | undefined) ?? null;
  return {
    token: kc.token ?? null,
    tokenParsed,
    userInfo,
    user: buildProfile(authType, tokenParsed, userInfo),
  };
}

/** Keycloak OIDC — loads UserInfo for name, email (upn), picture, groups. */
export class AuthKeycloak implements AuthProviderAdapter {
  readonly authType: AuthType;

  constructor(authType: AuthType = 'keycloak') {
    this.authType = authType;
  }

  async refreshSession(): Promise<AuthSession | null> {
    const kc = await getKeycloakInstance();
    if (!kc.authenticated) return null;
    const userInfo = await fetchUserInfo(kc);
    return sessionFromKeycloak(kc, this.authType, userInfo);
  }

  login(): void {
    const kc = keycloakSingleton;
    if (kc) kc.login();
  }

  logout(): void {
    const kc = keycloakSingleton;
    if (kc) kc.logout();
  }

  /** Refresh access token; returns updated session or null if logged out. */
  async refreshToken(minValidity = 70): Promise<AuthSession | null> {
    const kc = keycloakSingleton;
    if (!kc?.authenticated) return null;
    try {
      await kc.updateToken(minValidity);
      return this.refreshSession();
    } catch {
      return null;
    }
  }
}

export const authKeycloak = new AuthKeycloak('keycloak');
