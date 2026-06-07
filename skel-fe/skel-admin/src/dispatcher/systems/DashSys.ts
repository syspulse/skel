import type { DispatcherEvent, SysHandler } from '../types';

// Mirrors ExplainSys — same cmd contract: reload | add | update | del
export type DashSysCmd = (cmd: string, data: Record<string, unknown>) => void;

let callback: DashSysCmd | null = null;

export function registerDashSys(cb: DashSysCmd): () => void {
  callback = cb;
  return () => {
    if (callback === cb) callback = null;
  };
}

export const dashSysHandler: SysHandler = (event: DispatcherEvent): void => {
  if (!callback) return;
  callback(event.cmd ?? 'reload', event.data);
};
