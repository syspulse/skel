import React, { createContext, useCallback, useEffect, useRef, useState } from 'react';

export interface AuthUser {
  name: string;
  email?: string;
  roles?: string[];
}

export interface AuthState {
  token: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
  login: () => void;
  logout: () => void;
}

export const AuthContext = createContext<AuthState>({
  token: null,
  user: null,
  isAuthenticated: false,
  isLoading: true,
  error: null,
  login: () => {},
  logout: () => {},
});

const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED !== 'false';

const KEYCLOAK_URL =
  localStorage.getItem('VITE_KEYCLOAK_URL') ||
  import.meta.env.VITE_KEYCLOAK_URL ||
  'http://localhost:8180';
const KEYCLOAK_REALM =
  localStorage.getItem('VITE_KEYCLOAK_REALM') ||
  import.meta.env.VITE_KEYCLOAK_REALM ||
  'master';
const KEYCLOAK_CLIENT_ID =
  localStorage.getItem('VITE_KEYCLOAK_CLIENT_ID') ||
  import.meta.env.VITE_KEYCLOAK_CLIENT_ID ||
  'skel-admin';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type KeycloakInstance = any;

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);
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
        }
      } catch {
        setIsAuthenticated(false);
        setToken(null);
        setUser(null);
      }
    }, 60000);
  }, []);

  useEffect(() => {
    if (!AUTH_ENABLED) {
      // No-auth mode
      setToken(null);
      setUser({ name: 'Guest' });
      setIsAuthenticated(true);
      setIsLoading(false);
      return;
    }

    // Keycloak mode — dynamic import to avoid bundling issues in no-auth mode
    (async () => {
      try {
        const KeycloakModule = await import('keycloak-js');
        const Keycloak = KeycloakModule.default;
        const kc: KeycloakInstance = new Keycloak({
          url: KEYCLOAK_URL,
          realm: KEYCLOAK_REALM,
          clientId: KEYCLOAK_CLIENT_ID,
        });
        keycloakRef.current = kc;

        const authenticated = await kc.init({
          onLoad: 'login-required',
          checkLoginIframe: false,
        });

        if (authenticated) {
          setToken(kc.token ?? null);
          // Parse user info from token claims
          const parsed = kc.tokenParsed ?? {};
          setUser({
            name:
              (parsed.name as string) ||
              (parsed.preferred_username as string) ||
              'User',
            email: parsed.email as string | undefined,
            roles: (parsed.realm_access as { roles?: string[] } | undefined)
              ?.roles,
          });
          setIsAuthenticated(true);
          setupRefresh(kc);
        } else {
          setIsAuthenticated(false);
        }
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err);
        setError(`Keycloak init failed: ${msg}`);
        setIsAuthenticated(false);
      } finally {
        setIsLoading(false);
      }
    })();

    return () => {
      if (refreshIntervalRef.current) clearInterval(refreshIntervalRef.current);
    };
  }, [setupRefresh]);

  const login = useCallback(() => {
    if (keycloakRef.current) {
      keycloakRef.current.login();
    }
  }, []);

  const logout = useCallback(() => {
    if (!AUTH_ENABLED) {
      // no-op in guest mode — or reset to guest
      return;
    }
    if (keycloakRef.current) {
      keycloakRef.current.logout();
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{ token, user, isAuthenticated, isLoading, error, login, logout }}
    >
      {children}
    </AuthContext.Provider>
  );
}
