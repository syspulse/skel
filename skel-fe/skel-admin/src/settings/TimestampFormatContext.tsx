import React, { createContext, useContext, useState } from 'react';
import { DEFAULT_TIMESTAMP_FORMAT, normalizeTimestampPattern } from '../components/timestamp';

const STORAGE_KEY = 'timestampFormat';

interface TimestampFormatContextType {
  formatPattern: string;
  setFormatPattern: (pattern: string) => void;
}

const TimestampFormatContext = createContext<TimestampFormatContextType>({
  formatPattern: DEFAULT_TIMESTAMP_FORMAT,
  setFormatPattern: () => {},
});

function readStoredFormat(): string {
  const stored = localStorage.getItem(STORAGE_KEY);
  return normalizeTimestampPattern(stored ?? DEFAULT_TIMESTAMP_FORMAT);
}

export function TimestampFormatProvider({ children }: { children: React.ReactNode }) {
  const [formatPattern, setFormatPatternState] = useState<string>(() => {
    const normalized = readStoredFormat();
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored && normalizeTimestampPattern(stored) !== stored) {
      localStorage.setItem(STORAGE_KEY, normalized);
    }
    return normalized;
  });

  const setFormatPattern = (pattern: string) => {
    const value = normalizeTimestampPattern(pattern);
    localStorage.setItem(STORAGE_KEY, value);
    setFormatPatternState(value);
  };

  return (
    <TimestampFormatContext.Provider value={{ formatPattern, setFormatPattern }}>
      {children}
    </TimestampFormatContext.Provider>
  );
}

export function useTimestampFormat() {
  return useContext(TimestampFormatContext);
}
