import type { DispatcherEvent, SysHandler } from '../types';

// Supported commands: reload | add | update | del
// add    — data contains a full Explain object
// update — data contains a full Explain object (find by rid+oid and replace)
// del    — data contains { rid, oid? }
// reload — re-fetch from API (default for unknown commands too)
export type ExplainSysCmd = (cmd: string, data: Record<string, unknown>) => void;

let callback: ExplainSysCmd | null = null;

export function registerExplainSys(cb: ExplainSysCmd): () => void {
  callback = cb;
  return () => {
    if (callback === cb) callback = null;
  };
}

export const explainSysHandler: SysHandler = (event: DispatcherEvent): void => {
  if (!callback) return;
  callback(event.cmd ?? 'reload', event.data);
};
