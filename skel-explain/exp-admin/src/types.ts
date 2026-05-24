export interface ExplainScript {
  typ: string; // "js" | "ai" | "jq" | "regexp" | "str"
  src: string;
  opts?: string;
}

export interface Explain {
  oid?: string;
  rid: string;
  scripts: ExplainScript[];
  name?: string;
  desc?: string;
  sid?: string;
  meta?: Record<string, unknown>; // arbitrary JSON object; meta.icon for icon
  ts0: number;
  ts: number;
}

export interface Explains {
  data: Explain[];
  total?: number;
}

export interface ExplainCreateReq {
  oid?: string;
  scripts: ExplainScript[];
  name?: string;
  desc?: string;
  sid?: string;
  meta?: Record<string, unknown>;
}

export interface ExplainUpdateReq {
  scripts?: ExplainScript[];
  name?: string;
  desc?: string;
  sid?: string;
  meta?: Record<string, unknown>;
}

export interface ActionRes {
  oid?: string;
  rid: string;
}

export type TimeRange =
  | { type: 'last'; hours: number }
  | { type: 'custom'; start: Date; end: Date };
