import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import {
  BRANDING_CHANGE_EVENT,
  clearBrandingStorage,
  loadBranding,
  saveAppName,
  saveLogoUrl,
  DEFAULT_APP_NAME,
} from './branding';

export { DEFAULT_APP_NAME, APP_NAME_KEY, APP_LOGO_KEY } from './branding';

interface AppCtx {
  appName: string;
  logoUrl: string;
  setAppName: (name: string) => void;
  setLogoUrl: (url: string) => void;
  resetBranding: () => void;
}

const AppContext = createContext<AppCtx>({
  appName: DEFAULT_APP_NAME,
  logoUrl: '',
  setAppName: () => {},
  setLogoUrl: () => {},
  resetBranding: () => {},
});

export function AppProvider({ children }: { children: React.ReactNode }) {
  const [branding, setBranding] = useState(loadBranding);

  const applyBranding = useCallback((next: { appName: string; logoUrl: string }) => {
    setBranding(next);
    document.title = next.appName;
  }, []);

  useEffect(() => {
    document.title = branding.appName;
  }, [branding.appName]);

  useEffect(() => {
    const sync = () => applyBranding(loadBranding());
    window.addEventListener(BRANDING_CHANGE_EVENT, sync);
    window.addEventListener('storage', sync);
    return () => {
      window.removeEventListener(BRANDING_CHANGE_EVENT, sync);
      window.removeEventListener('storage', sync);
    };
  }, [applyBranding]);

  const setAppName = useCallback(
    (name: string) => {
      const display = saveAppName(name);
      setBranding((prev) => ({ ...prev, appName: display }));
      document.title = display;
    },
    [],
  );

  const setLogoUrl = useCallback((url: string) => {
    const trimmed = saveLogoUrl(url);
    setBranding((prev) => ({ ...prev, logoUrl: trimmed }));
  }, []);

  const resetBranding = useCallback(() => {
    applyBranding(clearBrandingStorage());
  }, [applyBranding]);

  const value = useMemo(
    () => ({
      appName: branding.appName,
      logoUrl: branding.logoUrl,
      setAppName,
      setLogoUrl,
      resetBranding,
    }),
    [branding.appName, branding.logoUrl, setAppName, setLogoUrl, resetBranding],
  );

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

export function useApp(): AppCtx {
  return useContext(AppContext);
}
