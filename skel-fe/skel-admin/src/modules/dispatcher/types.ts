export interface DispatcherEvent {
  id:    string;
  ts:    number;
  auth?: string;
  sev?:  number;        // 0.0–1.0  (0.0=success, 0.1=info, 0.3=warning, 0.5+=error)
  src?:  string;        // "local" | "notify" | "api" | "mcp" | ...
  dst?:  string;
  sys:   string;        // "" | "NotificationSys" | "ExplainSys" | "DashSys"
  typ?:  string;        // "COMMAND" | "ALERT" | "NOTIFY" | "SYSLOG" | "DATA"
  cmd?:  string;
  data:  Record<string, unknown>;
}

export type SysHandler = (event: DispatcherEvent) => void | Promise<void>;

export interface DispatcherStats {
  total: number;
  byTyp: Record<string, number>;
  bySys: Record<string, number>;
}
