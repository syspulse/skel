import { useCallback } from 'react';
import { useNotifications } from './NotificationContext';
import type { Severity } from './types';

/** Notification / popup title: "{Module} {error}" e.g. "Explain Load failed". */
export function moduleErrorTitle(module: string, error: string): string {
  return `${module} ${error}`;
}

/** Inline error line with optional detail. */
export function moduleErrorMessage(module: string, error: string, detail?: string): string {
  const title = moduleErrorTitle(module, error);
  return detail ? `${title}: ${detail}` : title;
}

export function useModuleNotify(module: string) {
  const { add } = useNotifications();

  const notify = useCallback((
    severity: Severity,
    error: string,
    message: string,
  ) => {
    add(severity, moduleErrorTitle(module, error), message, module);
  }, [add, module]);

  const notifyError = useCallback((error: string, message: string) => {
    notify('error', error, message);
  }, [notify]);

  return { notify, notifyError, moduleErrorTitle, moduleErrorMessage };
}
