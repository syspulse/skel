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
  schema?: Record<string, unknown>;    // JsonSchema of the config (like DetectorSchema.schema)
  uiSchema?: Record<string, unknown>;  // UI hints (like DetectorSchema.uiSchema)
  meta?: Meta;                         // arbitrary workflow metadata
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
  config?: Record<string, unknown>;    // config values per the schema (like DetectorConfig.config)
  graph: WorkflowGraf;
  oid?: string;
  pid?: string;
  xid?: string;
  meta?: Meta;            // engine metadata (e.g. { wid: "<WorkflowId>" })
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
  status: string; name: string; version: string;
  schema?: Record<string, unknown>;
  uiSchema?: Record<string, unknown>;
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
  meta?: Record<string, string>; // transient runtime metadata from /resolve (e.g. activity_id)
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
  author?: string; icon?: string; tags?: string[];
  schema?: Record<string, unknown>; uiSchema?: Record<string, unknown>; graph?: WorkflowGraf;
}
export interface WorkflowSchemaUpdateReq {
  name?: string; version?: string; title?: string; description?: string;
  status?: string; icon?: string; tags?: string[];
  schema?: Record<string, unknown>; uiSchema?: Record<string, unknown>; graph?: WorkflowGraf; meta?: Meta;
}
/** POST /schema/{id}/start body. Both fields optional. */
export interface WorkflowSchemaStartReq {
  input?: unknown;
  config?: Record<string, unknown>;
}
export interface WorkflowConfigCreateReq { sid: number; name?: string; oid?: string; pid?: string; xid?: string; }
export interface WorkflowConfigUpdateReq {
  name?: string; version?: string; title?: string; description?: string; author?: string;
  status?: string; icon?: string; tags?: string[]; config?: Record<string, unknown>; graph?: WorkflowGraf;
  oid?: string; pid?: string; xid?: string; meta?: Meta;
}
export interface WorkflowGrafCreateReq { id?: number; sid?: number; cid?: number; graph?: WorkflowGraf; }
export interface DetectorSchemaCreateReq {
  name: string; version?: string; title?: string; description?: string;
  author?: string; icon?: string; tags?: string[]; schema?: Record<string, unknown>; uiSchema?: Record<string, unknown>;
}
export interface DetectorConfigCreateReq {
  name: string; sid?: number; source?: string; status?: string; tags?: string[]; config?: Record<string, unknown>;
}
export interface DetectorSchemaUpdateReq {
  name?: string; version?: string; title?: string; description?: string;
  author?: string; status?: string; icon?: string; tags?: string[];
  schema?: Record<string, unknown>; uiSchema?: Record<string, unknown>;
}
export interface DetectorConfigUpdateReq {
  name?: string; status?: string; source?: string; tags?: string[]; config?: Record<string, unknown>;
  meta?: Record<string, string>;
}

// Single source of truth for the entity kinds used across tables / menus / details / editor.
export const KIND = {
  workflowSchema: 'workflow-schema',
  workflowConfig: 'workflow-config',
  detectorSchema: 'detector-schema',
  detectorConfig: 'detector-config',
  // combined read-only view: DetectorConfig enriched with its DetectorSchema fields
  detector: 'detector',
} as const;

export type EntityKind = (typeof KIND)[keyof typeof KIND];
export type WorkflowKind = typeof KIND.workflowSchema | typeof KIND.workflowConfig;
export type DetectorKind = typeof KIND.detectorSchema | typeof KIND.detectorConfig;

/** EntityKind -> i18n label key (the entity name shown in tabs / details titles). */
export function entityLabelKey(kind: EntityKind): string {
  switch (kind) {
    case KIND.workflowSchema: return 'workflow.tabs.workflowSchema';
    case KIND.workflowConfig: return 'workflow.tabs.workflowConfig';
    case KIND.detectorSchema: return 'workflow.tabs.detectorSchema';
    case KIND.detectorConfig: return 'workflow.tabs.detectorConfig';
    case KIND.detector: return 'workflow.tabs.detector';
  }
}
