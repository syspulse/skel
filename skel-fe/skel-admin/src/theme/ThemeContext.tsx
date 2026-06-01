import React, { createContext, useContext, useEffect, useState } from 'react';
import { type Theme, THEME_NAMES } from './palettes';

export type { Theme };

interface ThemeContextValue {
  theme: Theme;
  setTheme: (theme: Theme) => void;
}

const ThemeContext = createContext<ThemeContextValue>({
  theme: 'light',
  setTheme: () => {},
});

function isValidTheme(v: unknown): v is Theme {
  return THEME_NAMES.includes(v as Theme);
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => {
    const stored = localStorage.getItem('theme');
    return isValidTheme(stored) ? stored : 'light';
  });

  useEffect(() => {
    const root = document.documentElement;
    Array.from(root.classList)
      .filter(c => c.startsWith('theme-'))
      .forEach(c => root.classList.remove(c));
    root.classList.add(`theme-${theme}`);
    localStorage.setItem('theme', theme);
  }, [theme]);

  return (
    <ThemeContext.Provider value={{ theme, setTheme: setThemeState }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  return useContext(ThemeContext);
}
