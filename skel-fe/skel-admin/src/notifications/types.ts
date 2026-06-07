export const LOCAL_NOTIFICATION_SRC = 'local';

export type Severity = 'error' | 'warning' | 'info' | 'success';

export interface Notification {
  id: string;
  severity: Severity;
  title: string;
  message: string;
  src: string;
  ts: number;
  read: boolean;
}

export interface NotificationInput {
  severity: Severity;
  title: string;
  message: string;
  src: string;
  ts?: number;
}
