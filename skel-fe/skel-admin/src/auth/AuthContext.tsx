import React, { createContext, useCallback, useEffect, useRef, useState } from 'react';
import {
  clearStoredLoginMethod,
  getSkelAuthLoginUrl,
  getSkelGoogleLoginUrl,
  readStoredLoginMethod,
  storeLoginMethod,
} from './authConfig';
import {
  guestProfile,
  profileFromKeycloak,
  type UserProfile,
} from './userProfile';
import type { LoginOptionId } from './LoginScreen';

export type { UserProfile, AuthType } from './userProfile';

/** @deprecated Use UserProfile */
export type AuthUser = UserProfile;

export interface AuthState {
  token: string | null;
  tokenParsed: Record<string, unknown> | null;
  user: UserProfile | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  /** @deprecated use loginWithKeycloak */
  login: () => void;
  loginWithOption: (option: LoginOptionId) => void;
  loginAsGuest: () => void;
  loginWithKeycloak: () => void;
  loginWithSkel: () => void;
  loginWithGoogle: () => void;
  logout: () => void;
}

export const AuthContext = createContext<AuthState>({
  token: null,
  tokenParsed: null,
  user: null,
  isAuthenticated: false,
  isLoading: true,
  error: null,
  login: () => {},
  loginWithOption: () => {},
  loginAsGuest: () => {},
  loginWithKeycloak: () => {},
  loginWithSkel: () => {},
  loginWithGoogle: () => {},
  logout: () => {},
});

const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED !== 'false';

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

function applyKeycloakSession(
  kc: KeycloakInstance,
  setToken: (t: string | null) => void,
  setTokenParsed: (p: Record<string, unknown> | null) => void,
  setUser: (u: UserProfile | null) => void,
  setIsAuthenticated: (v: boolean) => void,
  setupRefresh: (kc: KeycloakInstance) => void,
) {
  storeLoginMethod('keycloak');
  setToken(kc.token ?? null);
  setTokenParsed((kc.tokenParsed as Record<string, unknown> | undefined) ?? null);
  setUser(profileFromKeycloak(kc));
  setIsAuthenticated(true);
  setupRefresh(kc);
}

function applyGuestSession(
  setToken: (t: string | null) => void,
  setTokenParsed: (p: Record<string, unknown> | null) => void,
  setUser: (u: UserProfile | null) => void,
  setIsAuthenticated: (v: boolean) => void,
) {
  storeLoginMethod('guest');
  setToken(null);
  setTokenParsed(null);
  setUser(guestProfile());
  setIsAuthenticated(true);
}

// Singleton — prevents double init under React StrictMode (which causes redirect loops).
let keycloakSingleton: KeycloakInstance | null = null;
let keycloakInitPromise: Promise<KeycloakInstance> | null = null;

async function getKeycloak(): Promise<KeycloakInstance> {
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

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [tokenParsed, setTokenParsed] = useState<Record<string, unknown> | null>(null);
  const [user, setUser] = useState<UserProfile | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const keycloakRef = useRef<KeycloakInstance>(null);
  const refreshIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const setupRefresh = useCallback((kc: KeycloakInstance) => {
    if (refreshIntervalRef.current) clearInterval(refreshIntervalRef.current);
    refreshIntervalRef.current = setInterval(async () => {
      try {
        const refreshed = await kc.updateToken(70);
        if (refreshed) {
          setToken(kc.token ?? null);
          setTokenParsed((kc.tokenParsed as Record<string, unknown> | undefined) ?? null);
          setUser(profileFromKeycloak(kc));
        }
      } catch {
        setIsAuthenticated(false);
        setToken(null);
        setTokenParsed(null);
        setUser(null);
        clearStoredLoginMethod();
      }
    }, 60000);
  }, []);

  const loginAsGuest = useCallback(() => {
    if (refreshIntervalRef.current) clearInterval(refreshIntervalRef.current);
    applyGuestSession(setToken, setTokenParsed, setUser, setIsAuthenticated);
    setError(null);
  }, []);

  const loginWithKeycloak = useCallback(() => {
    storeLoginMethod('keycloak');
    if (keycloakRef.current) {
      keycloakRef.current.login();
    }
  }, []);

  const loginWithSkel = useCallback(() => {
    storeLoginMethod('skel');
    window.location.href = getSkelAuthLoginUrl();
  }, []);

  const loginWithGoogle = useCallback(() => {
    storeLoginMethod('google');
    const kc = keycloakRef.current;
    if (kc) {
      kc.login({ idpHint: 'google' });
    } else {
      window.location.href = getSkelGoogleLoginUrl();
    }
  }, []);

  const loginWithOption = useCallback(
    (option: LoginOptionId) => {
      switch (option) {
        case 'guest':
          loginAsGuest();
          break;
        case 'skel':
          loginWithSkel();
          break;
        case 'keycloak':
          loginWithKeycloak();
          break;
        case 'google':
          loginWithGoogle();
          break;
      }
    },
    [loginAsGuest, loginWithSkel, loginWithKeycloak, loginWithGoogle],
  );

  useEffect(() => {
    if (!AUTH_ENABLED) {
      applyGuestSession(setToken, setTokenParsed, setUser, setIsAuthenticated);
      setIsLoading(false);
      return;
    }

    let cancelled = false;

    (async () => {
      try {
        const kc = await getKeycloak();
        if (cancelled) return;

        keycloakRef.current = kc;

        if (kc.authenticated) {
          applyKeycloakSession(
            kc,
            setToken,
            setTokenParsed,
            setUser,
            setIsAuthenticated,
            setupRefresh,
          );
        } else if (readStoredLoginMethod() === 'guest') {
          applyGuestSession(setToken, setTokenParsed, setUser, setIsAuthenticated);
        } else {
          setIsAuthenticated(false);
        }
      } catch (err) {
        if (cancelled) return;
        const msg = err instanceof Error ? err.message : String(err);
        setError(`Keycloak init failed: ${msg}`);
        if (readStoredLoginMethod() === 'guest') {
          applyGuestSession(setToken, setTokenParsed, setUser, setIsAuthenticated);
          setError(null);
        } else {
          setIsAuthenticated(false);
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    })();

    return () => {
      cancelled = true;
      if (refreshIntervalRef.current) clearInterval(refreshIntervalRef.current);
    };
  }, [setupRefresh]);

  const logout = useCallback(() => {
    if (refreshIntervalRef.current) clearInterval(refreshIntervalRef.current);
    clearStoredLoginMethod();

    const method = user?.authType;
    if (method === 'keycloak' && keycloakRef.current) {
      keycloakRef.current.logout();
      return;
    }

    setToken(null);
    setTokenParsed(null);
    setUser(null);
    setIsAuthenticated(false);
  }, [user?.authType]);

  return (
    <AuthContext.Provider
      value={{
        token,
        tokenParsed,
        user,
        isAuthenticated,
        isLoading,
        error,
        login: loginWithKeycloak,
        loginWithOption,
        loginAsGuest,
        loginWithKeycloak,
        loginWithSkel,
        loginWithGoogle,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
