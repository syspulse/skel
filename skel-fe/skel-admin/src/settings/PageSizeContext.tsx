import React, { createContext, useContext, useState } from 'react';

export const PAGE_SIZE_ALL = 0;
export const PAGE_SIZE_OPTIONS = [PAGE_SIZE_ALL, 10, 25, 50, 100] as const;
export const DEFAULT_PAGE_SIZE = 10;
const STORAGE_KEY = 'pageSize';

interface PageSizeContextType {
  pageSize: number;
  setPageSize: (size: number) => void;
}

const PageSizeContext = createContext<PageSizeContextType>({
  pageSize: DEFAULT_PAGE_SIZE,
  setPageSize: () => {},
});

function readStoredPageSize(): number {
  const stored = localStorage.getItem(STORAGE_KEY);
  const parsed = stored ? parseInt(stored, 10) : NaN;
  return (PAGE_SIZE_OPTIONS as readonly number[]).includes(parsed) ? parsed : DEFAULT_PAGE_SIZE;
}

export function isAllPages(pageSize: number): boolean {
  return pageSize === PAGE_SIZE_ALL;
}

export function PageSizeProvider({ children }: { children: React.ReactNode }) {
  const [pageSize, setPageSizeState] = useState<number>(readStoredPageSize);

  const setPageSize = (size: number) => {
    localStorage.setItem(STORAGE_KEY, String(size));
    setPageSizeState(size);
  };

  return (
    <PageSizeContext.Provider value={{ pageSize, setPageSize }}>
      {children}
    </PageSizeContext.Provider>
  );
}

export function usePageSize() {
  return useContext(PageSizeContext);
}
