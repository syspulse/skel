import type { DispatcherEvent, SysHandler } from '../types';
import type { NotificationInput, Severity } from '../../notifications/types';

// Severity mapping per spec: 0.0=success, 0.1=info, 0.3=warning, 0.5+=error
function sevToSeverity(sev?: number): Severity {
  if (sev === undefined || sev === null) return 'info';
  if (sev <= 0.0) return 'success';
  if (sev <= 0.15) return 'info';
  if (sev <= 0.45) return 'warning';
  return 'error';
}

// Called by NotificationContext — receives the internal _addDirect function (not the
// public push, to avoid the dispatch→route→dispatch cycle).
export function createNotificationSys(addDirect: (input: NotificationInput) => void): SysHandler {
  return (event: DispatcherEvent): void => {
    const severity =
      (event.data.severity as Severity | undefined) ?? sevToSeverity(event.sev);
    const title =
      (event.data.title as string | undefined) ??
      event.cmd ?? event.typ ?? 'Notification';
    const message = (event.data.message as string | undefined) ?? '';

    addDirect({
      severity,
      title,
      message,
      src: event.src ?? 'notify',
      ts: event.ts,
    });
  };
}
