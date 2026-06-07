/** Default app logo (Skel icon). */
export const DEFAULT_SKEL_LOGO_URL =
  (import.meta.env.VITE_DEFAULT_LOGO_URL as string | undefined)?.trim() ||
  'https://cdn-icons-png.flaticon.com/512/15632/15632573.png';

export function getSkelAuthUrl(): string {
  return (
    localStorage.getItem('VITE_SKEL_AUTH_URL') ||
    import.meta.env.VITE_SKEL_AUTH_URL ||
    'http://localhost:8080/api/v1/auth'
  ).replace(/\/$/, '');
}

export function getSkelAuthLoginUrl(): string {
  return `${getSkelAuthUrl()}/login`;
}

/** Google OAuth via skel-auth (server-side callback). */
export function getSkelGoogleLoginUrl(): string {
  const authBase = getSkelAuthUrl();
  const redirectUri = `${authBase}/callback/google`;
  const clientId =
    import.meta.env.VITE_GOOGLE_CLIENT_ID ||
    '1084039747276-3na59kcrc8ab5k65louvg5jv8ulnmv54.apps.googleusercontent.com';
  const scope = encodeURIComponent('openid profile email');
  return (
    `https://accounts.google.com/o/oauth2/v2/auth?response_type=code` +
    `&client_id=${encodeURIComponent(clientId)}` +
    `&scope=${scope}` +
    `&redirect_uri=${encodeURIComponent(redirectUri)}` +
    `&access_type=offline&prompt=consent`
  );
}

export const AUTH_SESSION_KEY = 'skel_auth_session';

export type LoginMethod = 'guest' | 'skel' | 'keycloak' | 'google';

export function readStoredLoginMethod(): LoginMethod | null {
  try {
    const raw = sessionStorage.getItem(AUTH_SESSION_KEY);
    if (!raw) return null;
    const method = JSON.parse(raw).method as LoginMethod;
    return method ?? null;
  } catch {
    return null;
  }
}

export function storeLoginMethod(method: LoginMethod): void {
  sessionStorage.setItem(AUTH_SESSION_KEY, JSON.stringify({ method }));
}

export function clearStoredLoginMethod(): void {
  sessionStorage.removeItem(AUTH_SESSION_KEY);
}
