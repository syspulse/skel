import React, { createContext, useContext, useState } from 'react';

const APP_NAME_KEY = 'app_name';
const APP_LOGO_KEY = 'app_logo';
export const DEFAULT_APP_NAME = 'admin';

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
  const [appName, setAppNameState] = useState<string>(
    () => localStorage.getItem(APP_NAME_KEY) || DEFAULT_APP_NAME,
  );
  const [logoUrl, setLogoUrlState] = useState<string>(
    () => localStorage.getItem(APP_LOGO_KEY) || '',
  );

  const setAppName = (name: string) => {
    const v = name || DEFAULT_APP_NAME;
    setAppNameState(v);
    if (v === DEFAULT_APP_NAME) {
      localStorage.removeItem(APP_NAME_KEY);
    } else {
      localStorage.setItem(APP_NAME_KEY, v);
    }
  };

  const setLogoUrl = (url: string) => {
    setLogoUrlState(url);
    if (url.trim()) {
      localStorage.setItem(APP_LOGO_KEY, url.trim());
    } else {
      localStorage.removeItem(APP_LOGO_KEY);
    }
  };

  const resetBranding = () => {
    localStorage.removeItem(APP_NAME_KEY);
    localStorage.removeItem(APP_LOGO_KEY);
    setAppNameState(DEFAULT_APP_NAME);
    setLogoUrlState('');
  };

  return (
    <AppContext.Provider value={{ appName, logoUrl, setAppName, setLogoUrl, resetBranding }}>
      {children}
    </AppContext.Provider>
  );
}

export function useApp(): AppCtx {
  return useContext(AppContext);
}
