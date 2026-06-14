export interface DashLayout {
  id: string;
  layout: Record<string, unknown>;
  name?: string;
  desc?: string;
  tags?: string[];
  pid?: string;
  tid?: string;
  ts: number;
  ts0: number;
}

export interface Dashs {
  data: DashLayout[];
  total?: number;
}

export interface DashCreateReq {
  layout: Record<string, unknown>;
  name?: string;
  desc?: string;
  tags?: string[];
  pid?: string;
  tid?: string;
}

export interface DashUpdateReq {
  id?: string;
  layout?: Record<string, unknown>;
  name?: string;
  desc?: string;
  tags?: string[];
  pid?: string;
  tid?: string;
}

export interface DashRes {
  id: string;
}
