export const APP_NAME_KEY = 'app_name';
export const APP_LOGO_KEY = 'app_logo';
export const DEFAULT_APP_NAME =
  (import.meta.env.VITE_APP_NAME as string | undefined)?.trim() || 'admin';
export const DEFAULT_APP_LOGO =
  (import.meta.env.VITE_APP_LOGO as string | undefined)?.trim() || '';

export const BRANDING_CHANGE_EVENT = 'skel-branding-change';

export interface Branding {
  appName: string;
  logoUrl: string;
}

declare global {
  interface Window {
    __SKEL_BRANDING__?: Branding;
  }
}

export function loadBranding(): Branding {
  if (typeof window !== 'undefined' && window.__SKEL_BRANDING__) {
    return { ...window.__SKEL_BRANDING__ };
  }

  try {
    const storedName = localStorage.getItem(APP_NAME_KEY);
    const storedLogo = localStorage.getItem(APP_LOGO_KEY);
    return {
      appName: storedName?.trim() || DEFAULT_APP_NAME,
      logoUrl: storedLogo?.trim() || DEFAULT_APP_LOGO,
    };
  } catch {
    return { appName: DEFAULT_APP_NAME, logoUrl: DEFAULT_APP_LOGO };
  }
}

export function saveAppName(name: string): string {
  const trimmed = name.trim();
  const display = trimmed || DEFAULT_APP_NAME;
  try {
    if (!trimmed || trimmed === DEFAULT_APP_NAME) {
      localStorage.removeItem(APP_NAME_KEY);
    } else {
      localStorage.setItem(APP_NAME_KEY, trimmed);
    }
  } catch {
    /* private mode / blocked storage */
  }
  if (typeof window !== 'undefined') {
    window.__SKEL_BRANDING__ = {
      ...(window.__SKEL_BRANDING__ ?? loadBranding()),
      appName: display,
    };
    notifyBrandingChange();
  }
  return display;
}

export function saveLogoUrl(url: string): string {
  const trimmed = url.trim();
  try {
    if (trimmed) {
      localStorage.setItem(APP_LOGO_KEY, trimmed);
    } else {
      localStorage.removeItem(APP_LOGO_KEY);
    }
  } catch {
    /* ignore */
  }
  if (typeof window !== 'undefined') {
    window.__SKEL_BRANDING__ = {
      ...(window.__SKEL_BRANDING__ ?? loadBranding()),
      logoUrl: trimmed,
    };
    notifyBrandingChange();
  }
  return trimmed;
}

export function clearBrandingStorage(): Branding {
  try {
    localStorage.removeItem(APP_NAME_KEY);
    localStorage.removeItem(APP_LOGO_KEY);
  } catch {
    /* ignore */
  }
  const defaults: Branding = { appName: DEFAULT_APP_NAME, logoUrl: DEFAULT_APP_LOGO };
  if (typeof window !== 'undefined') {
    window.__SKEL_BRANDING__ = defaults;
    notifyBrandingChange();
  }
  return defaults;
}

export function notifyBrandingChange(): void {
  window.dispatchEvent(new Event(BRANDING_CHANGE_EVENT));
}

/** URL / data URI / inline SVG unchanged; relative paths resolved against Vite base. */
export function resolveLogoSrc(logoUrl: string): string {
  const s = logoUrl.trim();
  if (!s || s.toLowerCase().startsWith('<svg')) return s;
  if (/^(https?:|data:|blob:)/i.test(s)) return s;
  if (s.startsWith('/')) return s;
  const base = import.meta.env.BASE_URL || '/';
  return `${base.replace(/\/?$/, '/')}${s.replace(/^\//, '')}`;
}

export function isInlineSvg(logoUrl: string): boolean {
  return logoUrl.trim().toLowerCase().startsWith('<svg');
}
