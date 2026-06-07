import React, { createContext, useCallback, useEffect, useRef, useState } from 'react';
import {
  clearStoredLoginMethod,
  readStoredLoginMethod,
  storeLoginMethod,
} from './authConfig';
import type { LoginOptionId } from './LoginScreen';
import {
  authKeycloak,
  getAuthProvider,
  getKeycloakInstance,
  loginMethodToAuthType,
  type AuthSession,
} from './providers';
import type { UserProfile } from './userProfile';

export type { UserProfile, AuthType } from './userProfile';

/** @deprecated Use UserProfile */
export type AuthUser = UserProfile;

function applySession(
  session: AuthSession,
  setToken: (t: string | null) => void,
  setTokenParsed: (p: Record<string, unknown> | null) => void,
  setUserInfo: (u: Record<string, unknown> | null) => void,
  setUser: (u: UserProfile | null) => void,
  setIsAuthenticated: (v: boolean) => void,
) {
  setToken(session.token);
  setTokenParsed(session.tokenParsed);
  setUserInfo(session.userInfo);
  setUser(session.user);
  setIsAuthenticated(true);
}

export interface AuthState {
  token: string | null;
  tokenParsed: Record<string, unknown> | null;
  userInfo: Record<string, unknown> | null;
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
  userInfo: null,
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

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [tokenParsed, setTokenParsed] = useState<Record<string, unknown> | null>(null);
  const [userInfo, setUserInfo] = useState<Record<string, unknown> | null>(null);
  const [user, setUser] = useState<UserProfile | null>(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const refreshIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const apply = useCallback((session: AuthSession) => {
    applySession(session, setToken, setTokenParsed, setUserInfo, setUser, setIsAuthenticated);
  }, []);

  const setupRefresh = useCallback(() => {
    if (refreshIntervalRef.current) clearInterval(refreshIntervalRef.current);
    refreshIntervalRef.current = setInterval(async () => {
      const method = readStoredLoginMethod();
      if (method === 'guest' || !method) return;

      const provider = getAuthProvider(method);
      if (provider.authType === 'keycloak' || provider.authType === 'google') {
        const session = await authKeycloak.refreshToken();
        if (session) {
          apply(session);
        } else {
          setIsAuthenticated(false);
          setToken(null);
          setTokenParsed(null);
          setUserInfo(null);
          setUser(null);
          clearStoredLoginMethod();
        }
      }
    }, 60000);
  }, [apply]);

  const loginAsGuest = useCallback(async () => {
    if (refreshIntervalRef.current) clearInterval(refreshIntervalRef.current);
    storeLoginMethod('guest');
    const session = await getAuthProvider('guest').refreshSession();
    if (session) apply(session);
    setError(null);
  }, [apply]);

  const loginWithKeycloak = useCallback(() => {
    storeLoginMethod('keycloak');
    getAuthProvider('keycloak').login();
  }, []);

  const loginWithSkel = useCallback(() => {
    storeLoginMethod('skel');
    getAuthProvider('skel').login();
  }, []);

  const loginWithGoogle = useCallback(() => {
    storeLoginMethod('google');
    getAuthProvider('google').login();
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
      getAuthProvider('guest').refreshSession().then((session) => {
        if (session) apply(session);
        setIsLoading(false);
      });
      return;
    }

    let cancelled = false;

    (async () => {
      try {
        const storedMethod = readStoredLoginMethod();

        if (storedMethod === 'guest') {
          const session = await getAuthProvider('guest').refreshSession();
          if (!cancelled && session) apply(session);
          return;
        }

        if (storedMethod === 'skel') {
          const session = await getAuthProvider('skel').refreshSession();
          if (!cancelled && session) {
            apply(session);
            setupRefresh();
          } else if (!cancelled) {
            setIsAuthenticated(false);
          }
          return;
        }

        // Keycloak / Google — init shared client first
        const kc = await getKeycloakInstance();
        if (cancelled) return;

        if (kc.authenticated) {
          const authType = loginMethodToAuthType(storedMethod ?? 'keycloak');
          const provider = getAuthProvider(authType);
          const session = await provider.refreshSession();
          if (session) {
            apply(session);
            setupRefresh();
          }
        } else {
          setIsAuthenticated(false);
        }
      } catch (err) {
        if (cancelled) return;
        const msg = err instanceof Error ? err.message : String(err);
        setError(`Keycloak init failed: ${msg}`);
        if (readStoredLoginMethod() === 'guest') {
          const session = await getAuthProvider('guest').refreshSession();
          if (session) apply(session);
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
  }, [apply, setupRefresh]);

  const logout = useCallback(() => {
    if (refreshIntervalRef.current) clearInterval(refreshIntervalRef.current);
    const method = readStoredLoginMethod();
    clearStoredLoginMethod();
    getAuthProvider(method).logout();

    if (method === 'keycloak' || method === 'google') {
      return;
    }

    setToken(null);
    setTokenParsed(null);
    setUserInfo(null);
    setUser(null);
    setIsAuthenticated(false);
  }, []);

  return (
    <AuthContext.Provider
      value={{
        token,
        tokenParsed,
        userInfo,
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
