import type { DispatcherEvent } from './types';

export interface DispatcherFilterState {
  src: string;
  sys: string;
  typ: string;
  cmd: string;
  sev: string;
  dst: string;
}

export const EMPTY_DISPATCHER_FILTERS: DispatcherFilterState = {
  src: '',
  sys: '',
  typ: '',
  cmd: '',
  sev: '',
  dst: '',
};

function matchField(value: string | number | undefined, filter: string): boolean {
  if (!filter.trim()) return true;
  const hay = String(value ?? '').toLowerCase();
  const needle = filter.trim().toLowerCase();
  return hay.includes(needle);
}

export function filterDispatcherEvents(
  events: readonly DispatcherEvent[],
  filters: DispatcherFilterState,
): DispatcherEvent[] {
  return events.filter((e) =>
    matchField(e.src, filters.src)
    && matchField(e.sys, filters.sys)
    && matchField(e.typ, filters.typ)
    && matchField(e.cmd, filters.cmd)
    && matchField(e.sev, filters.sev)
    && matchField(e.dst, filters.dst),
  );
}

export function uniqueFieldValues(
  events: readonly DispatcherEvent[],
  pick: (e: DispatcherEvent) => string | number | undefined,
): string[] {
  const set = new Set<string>();
  for (const e of events) {
    const v = pick(e);
    if (v !== undefined && v !== '') set.add(String(v));
  }
  return Array.from(set).sort((a, b) => a.localeCompare(b));
}
