import { authHeaders, handleResponse } from '../../api';
import type {
  WorkflowSchema, WorkflowSchemas, WorkflowSchemaView, WorkflowSchemaCreateReq, WorkflowSchemaUpdateReq,
  WorkflowConfig, WorkflowConfigs, WorkflowConfigView, WorkflowConfigCreateReq, WorkflowConfigUpdateReq,
  WorkflowGraf, WorkflowGrafs, WorkflowGrafCreateReq,
  DetectorSchema, DetectorSchemas, DetectorSchemaCreateReq, DetectorSchemaUpdateReq,
  DetectorConfig, DetectorConfigs, DetectorConfigCreateReq, DetectorConfigUpdateReq,
  WorkflowActionRes,
} from './types';

const WORKFLOW_URL_KEY = 'VITE_WORKFLOW_API_URL';

function getBaseUrl(): string {
  const stored = localStorage.getItem(WORKFLOW_URL_KEY);
  if (stored) return stored;
  return import.meta.env.VITE_WORKFLOW_API_URL || 'http://localhost:8080/api/v1/wf/ext';
}

function pageQuery(from?: number, size?: number, entity?: string): string {
  const p = new URLSearchParams();
  if (from !== undefined) p.set('from', String(from));
  if (size !== undefined) p.set('size', String(size));
  if (entity) p.set('entity', entity);
  const q = p.toString();
  return q ? `?${q}` : '';
}

async function GET<T>(token: string | null, path: string): Promise<T> {
  const res = await fetch(`${getBaseUrl()}${path}`, { headers: authHeaders(token) });
  return handleResponse<T>(res);
}
async function POST<T>(token: string | null, path: string, body: unknown): Promise<T> {
  const res = await fetch(`${getBaseUrl()}${path}`, { method: 'POST', headers: authHeaders(token), body: JSON.stringify(body) });
  return handleResponse<T>(res);
}
async function PUT<T>(token: string | null, path: string, body: unknown): Promise<T> {
  const res = await fetch(`${getBaseUrl()}${path}`, { method: 'PUT', headers: authHeaders(token), body: JSON.stringify(body) });
  return handleResponse<T>(res);
}
async function DEL<T>(token: string | null, path: string): Promise<T> {
  const res = await fetch(`${getBaseUrl()}${path}`, { method: 'DELETE', headers: authHeaders(token) });
  return handleResponse<T>(res);
}

// ---------------------------------------------------------------- WorkflowSchema
// `detail` pulls the referenced DetectorSchema map alongside the graph -> entity=graf,schema.
// Without detail the default (graf) still returns the graph (needed for the node/link counts).
export const listSchemas = (token: string | null, from?: number, size?: number, detail?: boolean) =>
  GET<WorkflowSchemas>(token, `/schema${pageQuery(from, size, detail ? 'graf,schema' : undefined)}`);
export const getSchema = (token: string | null, id: number, detail?: boolean) =>
  GET<WorkflowSchemaView>(token, `/schema/${id}${detail ? '?entity=graf,schema' : ''}`);
export const createSchema = (token: string | null, req: WorkflowSchemaCreateReq) =>
  POST<WorkflowSchema>(token, '/schema', req);
export const createSchemaDsl = (token: string | null, pipeline: string, name?: string) =>
  POST<WorkflowSchema>(token, '/schema/dsl', { pipeline, name });
export const updateSchema = (token: string | null, id: number, req: WorkflowSchemaUpdateReq) =>
  PUT<WorkflowSchema>(token, `/schema/${id}`, req);
export const deleteSchema = (token: string | null, id: number) =>
  DEL<WorkflowActionRes>(token, `/schema/${id}`);

// ---------------------------------------------------------------- WorkflowConfig
// NOTE: the config views don't consume `detectors`/`schemas` (the editor uses the separate detector
// lists + /resolve) - only the graph. The default (graf) returns exactly that. Pass
// 'detector'/'schema'/'all' (CSV) to `entity` only if the maps are needed.
export const listConfigs = (token: string | null, from?: number, size?: number) =>
  GET<WorkflowConfigs>(token, `/config${pageQuery(from, size)}`);
export const getConfig = (token: string | null, id: number) =>
  GET<WorkflowConfigView>(token, `/config/${id}`);
export const createConfig = (token: string | null, req: WorkflowConfigCreateReq) =>
  POST<WorkflowConfig>(token, '/config', req);
// create a WorkflowConfig from a WorkflowSchema id in one call (composed of DetectorConfig; ids from
// store). contractId places the new DetectorConfigs under a contract (default 0 - see setup0).
export const createConfigFromSchema = (token: string | null, sid: number, contractId = 0) =>
  POST<WorkflowConfig>(token, `/config/schema/${sid}?contractId=${contractId}`, {});
export const createConfigDsl = (token: string | null, pipeline: string, name?: string) =>
  POST<WorkflowConfig>(token, '/config/dsl', { pipeline, name });
export const updateConfig = (token: string | null, id: number, req: WorkflowConfigUpdateReq) =>
  PUT<WorkflowConfig>(token, `/config/${id}`, req);
export const deleteConfig = (token: string | null, id: number) =>
  DEL<WorkflowActionRes>(token, `/config/${id}`);
// resolve WorkflowConfig(s) + all DetectorConfigs by runtimeId/workflowId, with live engine-mapped statuses.
// `ids` is a comma-separated list; `type` forces the mode ('rid' | 'wid'), default auto-detect.
export const resolveConfigs = (token: string | null, ids: string, type?: string) =>
  GET<WorkflowConfigs>(token, `/config/resolve/${encodeURIComponent(ids)}${type ? `?type=${type}` : ''}`);

// ---------------------------------------------------------------- WorkflowGraf
export const listGrafs = (token: string | null, from?: number, size?: number) =>
  GET<WorkflowGrafs>(token, `/graf${pageQuery(from, size)}`);
export const getGraf = (token: string | null, id: number) =>
  GET<WorkflowGraf>(token, `/graf/${id}`);
export const createGraf = (token: string | null, req: WorkflowGrafCreateReq) =>
  POST<WorkflowGraf>(token, '/graf', req);
export const deleteGraf = (token: string | null, id: number) =>
  DEL<WorkflowActionRes>(token, `/graf/${id}`);

// ---------------------------------------------------------------- DetectorSchema
export const listDetectorSchemas = (token: string | null, from?: number, size?: number) =>
  GET<DetectorSchemas>(token, `/detector/schema${pageQuery(from, size)}`);
export const getDetectorSchema = (token: string | null, id: number) =>
  GET<DetectorSchema>(token, `/detector/schema/${id}`);
export const createDetectorSchema = (token: string | null, req: DetectorSchemaCreateReq) =>
  POST<DetectorSchema>(token, '/detector/schema', req);
export const updateDetectorSchema = (token: string | null, id: number, req: DetectorSchemaUpdateReq) =>
  PUT<DetectorSchema>(token, `/detector/schema/${id}`, req);
export const deleteDetectorSchema = (token: string | null, id: number) =>
  DEL<WorkflowActionRes>(token, `/detector/schema/${id}`);

// ---------------------------------------------------------------- DetectorConfig
export const listDetectorConfigs = (token: string | null, from?: number, size?: number) =>
  GET<DetectorConfigs>(token, `/detector/config${pageQuery(from, size)}`);
export const getDetectorConfig = (token: string | null, id: number) =>
  GET<DetectorConfig>(token, `/detector/config/${id}`);
export const createDetectorConfig = (token: string | null, req: DetectorConfigCreateReq) =>
  POST<DetectorConfig>(token, '/detector/config', req);
export const updateDetectorConfig = (token: string | null, id: number, req: DetectorConfigUpdateReq) =>
  PUT<DetectorConfig>(token, `/detector/config/${id}`, req);
export const deleteDetectorConfig = (token: string | null, id: number) =>
  DEL<WorkflowActionRes>(token, `/detector/config/${id}`);
