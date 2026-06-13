// TypeScript types mirroring the wf-ext backend JSON (io.hacken.ext.wf / io.hacken.ext.detector).
// Int-keyed Scala maps (nodes/links) are serialized as string-keyed JSON objects.

export type Meta = Record<string, unknown>;

export interface WorkflowLink {
  id: number;
  from: number;
  to: number;
  typ?: string;
  meta?: Meta;
  data?: Record<string, unknown>;
}

export interface WorkflowNode {
  id: number;
  title: string;
  sid: number;            // DetectorSchema id
  cid?: number;           // DetectorConfig id (set for config instances)
  typ?: string;
  icon?: string;
  tags?: string[];
  desc?: string;
  meta?: Meta;            // visual: pos_x/pos_y/size_width/size_height/color/border/style
  data?: Record<string, unknown>;
  links?: Record<string, WorkflowLink>;
}

export interface WorkflowGraf {
  id: number;
  sid?: number;           // WorkflowSchema id
  cid?: number;           // WorkflowConfig id (None -> template, Some -> instance)
  nodes: Record<string, WorkflowNode>;
  links: Record<string, WorkflowLink>;
  meta?: Meta;
  data?: Record<string, unknown>;
}

export interface WorkflowSchemaFaq { name: string; value: string; }

export interface WorkflowSchema {
  id: number;
  createdAt: number;
  updatedAt: number;
  status: string;
  name: string;
  version: string;
  title: string;
  description: string;
  author: string;
  icon?: string;
  faq?: WorkflowSchemaFaq[];
  tags: string[];
  graph: WorkflowGraf;
}

export interface WorkflowConfig {
  id: number;
  sid: number;
  createdAt: number;
  updatedAt: number;
  status: string;
  name: string;
  version: string;
  title: string;
  description: string;
  author: string;
  icon?: string;
  tags: string[];
  graph: WorkflowGraf;
  oid?: string;
  pid?: string;
  xid?: string;
}

// ---- Detector entities ----
export interface DetectorSchema {
  id: number;
  createdAt: number;
  updatedAt: number;
  status: string;
  name: string;
  version: string;
  title: string;
  description: string;
  author: string;
  icon?: string;
  faq?: WorkflowSchemaFaq[];
  tags: string[];
  networkTags: string[];
  schema?: Record<string, unknown>;
  uiSchema?: Record<string, unknown>;
}

export interface DetectorConfigContract {
  id: number; createdAt: number; updatedAt: number;
  projectId: number; tenantId: number;
  chainUid?: string; proxyAddress?: string; implementation?: string; address?: string;
  name: string;
}
export interface DetectorConfigSchemaRef {
  id: number; createdAt: number; updatedAt: number;
  status: string; name: string; version: string; schema?: Record<string, unknown>;
}
export interface DetectorConfig {
  id: number;
  createdAt: number;
  updatedAt: number;
  status: string;
  contract: DetectorConfigContract;
  schema?: DetectorConfigSchemaRef;
  name: string;
  source: string;
  tags: string[];
  config?: Record<string, unknown>;
  destinations: unknown[];
}

// ---- response wrappers ----
export interface WorkflowSchemas { schemas: WorkflowSchema[]; total: number; detectors?: Record<string, DetectorSchema>; }
export interface WorkflowConfigs { configs: WorkflowConfig[]; total: number; detectors?: Record<string, DetectorConfig>; }
export interface WorkflowGrafs { grafs: WorkflowGraf[]; total: number; }
export interface WorkflowSchemaView { schema: WorkflowSchema; detectors?: Record<string, DetectorSchema>; }
export interface WorkflowConfigView { config: WorkflowConfig; detectors?: Record<string, DetectorConfig>; }
export interface DetectorSchemas { schemas: DetectorSchema[]; total: number; }
export interface DetectorConfigs { configs: DetectorConfig[]; total: number; }
export interface WorkflowActionRes { status: string; id?: number; }

// ---- request bodies ----
export interface WorkflowSchemaCreateReq {
  name: string; version?: string; title?: string; description?: string;
  author?: string; icon?: string; tags?: string[]; graph?: WorkflowGraf;
}
export interface WorkflowSchemaUpdateReq {
  name?: string; version?: string; title?: string; description?: string;
  status?: string; icon?: string; tags?: string[]; graph?: WorkflowGraf;
}
export interface WorkflowConfigCreateReq { sid: number; name?: string; oid?: string; pid?: string; xid?: string; }
export interface WorkflowConfigUpdateReq {
  name?: string; version?: string; title?: string; description?: string;
  status?: string; icon?: string; tags?: string[]; graph?: WorkflowGraf;
  oid?: string; pid?: string; xid?: string;
}
export interface WorkflowGrafCreateReq { id?: number; sid?: number; cid?: number; graph?: WorkflowGraf; }
export interface DetectorSchemaCreateReq {
  name: string; version?: string; title?: string; description?: string;
  author?: string; icon?: string; tags?: string[]; schema?: Record<string, unknown>; uiSchema?: Record<string, unknown>;
}
export interface DetectorConfigCreateReq {
  name: string; sid?: number; source?: string; status?: string; tags?: string[]; config?: Record<string, unknown>;
}
export interface DetectorConfigUpdateReq {
  name?: string; status?: string; source?: string; tags?: string[]; config?: Record<string, unknown>;
}

// Which entity kind a table/menu refers to.
export type EntityKind = 'schema' | 'config' | 'detector-schema' | 'detector-config';
