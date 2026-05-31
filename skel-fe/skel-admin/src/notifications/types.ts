export type Severity = 'error' | 'warning' | 'info' | 'success';

export interface Notification {
  id: string;
  severity: Severity;
  title: string;
  message: string;
  ts: number;
  read: boolean;
}
