import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ModulePage } from '../../components/ModulePage';
import { useAuth } from '../../auth/useAuth';
import { useModuleNotify } from '../../notifications/moduleNotify';
import { usePageSize, PAGE_SIZE_ALL } from '../../settings/PageSizeContext';
import { useDefaultOid } from '../../settings/OwnerContext';
import { Pagination } from '../../components/Pagination';
import * as api from './api';
import type {
  WorkflowSchema, WorkflowConfig, DetectorSchema, DetectorConfig, WorkflowGraf, EntityKind, WorkflowKind, DetectorKind,
} from './types';
import { entityLabelKey, KIND } from './types';
import { EntityTable, type TableRow, type TableColumn } from './components/EntityTable';
import { WorkflowFilters, type WorkflowFilterState } from './components/WorkflowFilters';
import type { TimeRange } from '../../types';
import { DEFAULT_SCHEMA_ICON, DEFAULT_CONFIG_ICON, DEFAULT_WF_SCHEMA_ICON, DEFAULT_WF_CONFIG_ICON } from '../../components/IconPicker';
import { WorkflowSlider } from './components/WorkflowSlider';
import { DetectorSlider } from './components/DetectorSlider';
import { SchemaStartDialog, metaInputText, metaInputDataText } from './components/SchemaStartDialog';
import { WorkflowEditor } from './editor/WorkflowEditor';
import { dispatcher } from '../dispatcher/Dispatcher';

export interface WorkflowEditTarget { kind: WorkflowKind; id: number; }

interface WorkflowPageProps {
  /** Deep-link from the SideNav submenu: open the editor for this instance. */
  editTarget?: WorkflowEditTarget | null;
  /** Bumped when the main "Workflow" menu is clicked: exit the editor and show the tabs UI. */
  homeKey?: number;
  onEditTargetApplied?: () => void;
  /** Notify the SideNav that instances changed (so it can refresh submenus). */
  onInstancesChanged?: () => void;
  /** Notify which instance the editor is on (so the SideNav submenu highlight stays in sync). */
  onActiveInstanceChange?: (target: WorkflowEditTarget | null) => void;
}

const TAB_IDS: EntityKind[] = [KIND.workflowSchema, KIND.workflowConfig, KIND.detectorSchema, KIND.detectorConfig, KIND.detector];

interface EditorState {
  kind: WorkflowKind;
  id: number;
  title: string;
  name: string;
  icon?: string;
  status?: string;   // WorkflowConfig runtime status (updated by Resolve)
  xid?: string;      // WorkflowConfig engine runtime id (updated by Resolve)
  engineUrl?: string; // WorkflowConfig meta.url: deep-link to the run on the engine panel
  engine?: string;    // WorkflowConfig meta.engine: engine name (selects the panel-link icon)
  graf: WorkflowGraf;
}

function isInTimeRange(ts: number, range: TimeRange): boolean {
  if (range.type === 'all') return true;
  const now = Date.now();
  if (range.type === 'last') return ts >= now - range.hours * 60 * 60 * 1000;
  return ts >= range.start.getTime() && ts <= range.end.getTime();
}
const matchText = (hay: string, q: string) => !q || hay.toLowerCase().includes(q.toLowerCase());
const matchStatus = (status: string, f: string) => !f || status.toLowerCase().includes(f.toLowerCase());
// id filter: empty -> match all; otherwise a single id or CSV list ("3" or "1,2,5").
const matchIds = (id: number, csv: string): boolean => {
  const wanted = csv.split(',').map((s) => s.trim()).filter(Boolean);
  return wanted.length === 0 || wanted.includes(String(id));
};

export function WorkflowPage({ editTarget, homeKey, onEditTargetApplied, onInstancesChanged, onActiveInstanceChange }: WorkflowPageProps) {
  const { t } = useTranslation();
  const { token } = useAuth();
  const { notifyError } = useModuleNotify(t('nav.workflow'));
  const { pageSize, setPageSize } = usePageSize();
  const defaultOid = useDefaultOid();  // default owner id for create/start (from user profile settings)
  const [page, setPage] = useState(1);
  const [timezone, setTimezone] = useState('local');
  const [activeSearch, setActiveSearch] = useState('');
  const [filters, setFilters] = useState<WorkflowFilterState>({ ids: '', search: '', status: '', timeRange: { type: 'all' } });

  const handleFilterChange = (f: WorkflowFilterState) => {
    if (f.ids !== filters.ids || f.status !== filters.status || f.timeRange !== filters.timeRange) setPage(1);
    setFilters(f);
  };
  const handleSearch = (q: string) => { setActiveSearch(q); setPage(1); };

  const [schemas, setSchemas] = useState<WorkflowSchema[]>([]);
  const [configs, setConfigs] = useState<WorkflowConfig[]>([]);
  const [detSchemas, setDetSchemas] = useState<DetectorSchema[]>([]);
  const [detConfigs, setDetConfigs] = useState<DetectorConfig[]>([]);

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [sliderKind, setSliderKind] = useState<EntityKind | null>(null);
  const [addMode, setAddMode] = useState(false);
  const [saving, setSaving] = useState(false);
  const [startOpen, setStartOpen] = useState(false);                        // WorkflowSchema Start dialog
  const [startSchemaId, setStartSchemaId] = useState<number | null>(null);  // preset schema (row Start); null = pick in dialog
  const [resolving, setResolving] = useState(false);
  // cid -> DetectorConfig runtime status from the last /resolve (overlaid on the editor graph nodes)
  const [resolvedDetStatus, setResolvedDetStatus] = useState<Record<number, string>>({});
  const [resolvedDetActivity, setResolvedDetActivity] = useState<Record<number, string>>({});
  // Track: auto-poll /resolve for the given config id at `freq` ms (null = not tracking)
  const [freq, setFreq] = useState(3000);
  const [trackingId, setTrackingId] = useState<number | null>(null);
  const [pollCount, setPollCount] = useState(0); // number of Track polls executed in the current session

  const [editor, setEditor] = useState<EditorState | null>(null);
  // detector detail opened from the editor (sid/cid [->]) - read-only overlay
  const [detView, setDetView] = useState<{ kind: DetectorKind; id: number } | null>(null);
  // WorkflowSchema/Config details opened from the editor Panel 1 [->]
  const [wfDetailsOpen, setWfDetailsOpen] = useState(false);

  const fetchAll = useCallback(async () => {
    try {
      const [s, c, ds, dc] = await Promise.all([
        api.listSchemas(token), api.listConfigs(token),
        api.listDetectorSchemas(token), api.listDetectorConfigs(token),
      ]);
      setSchemas(s.schemas ?? []);
      setConfigs(c.configs ?? []);
      setDetSchemas(ds.schemas ?? []);
      setDetConfigs(dc.configs ?? []);
    } catch (e) {
      notifyError(t('workflow.errorLoad'), e instanceof Error ? e.message : String(e));
    }
  }, [token, notifyError, t]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  // ----- open the editor for a schema/config instance -----
  const openEditor = useCallback(async (kind: WorkflowKind, id: number) => {
    try {
      setResolvedDetStatus({}); // start clean; Resolve overlays live statuses on demand
      setResolvedDetActivity({});
      if (kind === KIND.workflowSchema) {
        const v = await api.getSchema(token, id, true);
        setDetSchemas((cur) => mergeDetectors(cur, v.detectors));
        setEditor({ kind, id, title: v.schema.title, name: v.schema.name, icon: v.schema.icon, graf: v.schema.graph });
      } else {
        const v = await api.getConfig(token, id);
        setEditor({ kind, id, title: v.config.title, name: v.config.name, icon: v.config.icon, status: v.config.status, xid: v.config.xid, engineUrl: v.config.meta?.url ? String(v.config.meta.url) : undefined, engine: v.config.meta?.engine ? String(v.config.meta.engine) : undefined, graf: v.config.graph });
      }
      onActiveInstanceChange?.({ kind, id }); // keep the SideNav submenu highlight in sync with the editor
    } catch (e) {
      notifyError(t('workflow.errorLoad'), e instanceof Error ? e.message : String(e));
    }
  }, [token, notifyError, t, onActiveInstanceChange]);

  // ----- Resolve: fetch current engine state (WorkflowConfig + DetectorConfig statuses) via /resolve -----
  // `background` (used by the Track polling loop) skips the `resolving` flag so the Resolve/Track
  // buttons are NOT toggled (disabled/label) on every automatic poll - only a manual click shows it.
  const resolveConfig = useCallback(async (c: WorkflowConfig, background = false) => {
    // Resolve requires a real runtime handle (xid, or meta.wid) - NEVER fall back to the config name
    // (a config without an xid has no run to resolve; the Resolve button is disabled for it).
    const rid = c.xid || (c.meta?.wid ? String(c.meta.wid) : '');
    if (!rid) return;
    if (!background) setResolving(true);
    try {
      const res = await api.resolveConfigs(token, rid);
      const rc = res.configs.find((x) => x.id === c.id) ?? res.configs[0];
      if (rc) {
        setConfigs((cur) => cur.map((x) => (x.id === rc.id ? { ...x, status: rc.status, xid: rc.xid, meta: rc.meta } : x)));
        setEditor((e) => (e && e.kind === KIND.workflowConfig && e.id === rc.id ? { ...e, status: rc.status, xid: rc.xid, engineUrl: rc.meta?.url ? String(rc.meta.url) : e.engineUrl, engine: rc.meta?.engine ? String(rc.meta.engine) : e.engine } : e));
      }
      const map: Record<number, string> = {};
      const actMap: Record<number, string> = {};
      if (res.detectors) for (const d of Object.values(res.detectors)) {
        map[d.id] = d.status;
        const aid = d.meta?.activity_id;
        if (aid) actMap[d.id] = aid;
      }
      setResolvedDetStatus(map);
      setResolvedDetActivity(actMap);
      // reflect the live DetectorConfig statuses + activity_id in the detector list too
      setDetConfigs((cur) => cur.map((d) => (map[d.id] !== undefined ? { ...d, status: map[d.id], meta: res.detectors?.[String(d.id)]?.meta ?? d.meta } : d)));
    } catch (e) {
      if (!background) notifyError(t('workflow.errorResolve'), e instanceof Error ? e.message : String(e));
    } finally {
      if (!background) setResolving(false);
    }
  }, [token, notifyError, t]);

  // Resolve ALL WorkflowConfigs in the table view (by xid) in one call and update their statuses.
  const resolveAllConfigs = useCallback(async () => {
    const ids = configs.map((c) => c.xid).filter((x): x is string => !!x);
    if (ids.length === 0) return;
    setResolving(true);
    try {
      const res = await api.resolveConfigs(token, ids.join(','), 'rid');
      const byId = new Map(res.configs.map((rc) => [rc.id, rc] as const));
      setConfigs((cur) => cur.map((c) => { const rc = byId.get(c.id); return rc ? { ...c, status: rc.status, xid: rc.xid, meta: rc.meta } : c; }));
      if (res.detectors) {
        const dmap: Record<number, string> = {};
        for (const d of Object.values(res.detectors)) dmap[d.id] = d.status;
        setDetConfigs((cur) => cur.map((d) => (dmap[d.id] !== undefined ? { ...d, status: dmap[d.id] } : d)));
      }
    } catch (e) {
      notifyError(t('workflow.errorResolve'), e instanceof Error ? e.message : String(e));
    } finally {
      setResolving(false);
    }
  }, [configs, token, notifyError, t]);

  // Emit a Dispatcher notification (sev: 0.1=info, 0.5=error -> NotificationSys maps to bell severity).
  const notifyDispatcher = useCallback((sev: number, title: string, message: string, data: Record<string, unknown> = {}) => {
    dispatcher.dispatch({
      id: (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : String(Date.now()),
      ts: Date.now(),
      src: 'workflow',
      sys: 'NotificationSys',
      typ: 'NOTIFY',
      sev,
      data: { title, message, ...data },
    });
  }, []);

  // Start or spawn a WorkflowConfig from a WorkflowSchema (Start dialog).
  // Non-empty input_data is written to schema.meta.input_data first so the backend uses it.
  const startOrSpawnFromSchema = useCallback(async (
    mode: 'start' | 'spawn',
    input: unknown | undefined, taskQueue?: string, wid?: string, ns?: string, oid?: string, pid?: string, config?: Record<string, unknown>, inputData?: string,
  ) => {
    if (startSchemaId === null) return;
    const sid = startSchemaId;
    setSaving(true);
    try {
      const latest = (await api.getSchema(token, sid)).schema;
      const meta = { ...(latest.meta ?? {}) };
      const data = inputData?.trim();
      const prev = meta.input_data == null ? '' : String(meta.input_data).trim();
      if (data) meta.input_data = data;
      else delete meta.input_data;
      if ((data ?? '') !== prev) await api.updateSchema(token, sid, { meta });
      const res = mode === 'start'
        ? await api.startSchema(token, sid, input, taskQueue, wid, ns, oid, pid, config)
        : await api.spawnSchema(token, sid, input, taskQueue, wid, ns, oid, pid, config);
      const c = res.configs?.[0];
      const okTitle = mode === 'start' ? t('workflow.startOk') : t('workflow.spawnOk');
      if (c) notifyDispatcher(0.1, okTitle,
        t('workflow.startOkMsg', { id: c.id, name: c.name, title: c.title }),
        { schema_id: sid, config_id: c.id, config_name: c.name, config_title: c.title });
      else notifyDispatcher(0.1, okTitle, t('workflow.startOkMsg', { id: '?', name: '', title: '' }), { schema_id: sid });
      setStartOpen(false);
      await refreshAndNotify();
      if (c) await openEditor(KIND.workflowConfig, c.id);
    } catch (e) {
      notifyDispatcher(0.5, mode === 'start' ? t('workflow.startErr') : t('workflow.spawnErr'), e instanceof Error ? e.message : String(e), { schema_id: sid });
    } finally {
      setSaving(false);
    }
  }, [startSchemaId, token, notifyDispatcher, t, openEditor]);

  const startFromSchema = useCallback((input: unknown | undefined, taskQueue?: string, wid?: string, ns?: string, oid?: string, pid?: string, config?: Record<string, unknown>, inputData?: string) =>
    startOrSpawnFromSchema('start', input, taskQueue, wid, ns, oid, pid, config, inputData), [startOrSpawnFromSchema]);
  const spawnFromSchema = useCallback((input: unknown | undefined, taskQueue?: string, wid?: string, ns?: string, oid?: string, pid?: string, config?: Record<string, unknown>, inputData?: string) =>
    startOrSpawnFromSchema('spawn', input, taskQueue, wid, ns, oid, pid, config, inputData), [startOrSpawnFromSchema]);

  // Validate a cid before a node re-link: it must resolve via GET /detector/config/{cid}.
  // On not-found, dispatch an error event (Dispatcher) and reject the change.
  const validateCid = useCallback(async (cid: number): Promise<boolean> => {
    try {
      await api.getDetectorConfig(token, cid);
      return true;
    } catch (e) {
      notifyError(t('workflow.errorCidNotFound', { id: cid }), e instanceof Error ? e.message : String(e));
      return false;
    }
  }, [token, notifyError, t]);

  // keep a ref to the latest configs so the tracking interval always resolves the current object (xid may change)
  const configsRef = useRef<WorkflowConfig[]>(configs);
  useEffect(() => { configsRef.current = configs; }, [configs]);

  // Track: while a config id is tracked, poll /resolve every `freq` ms (resolves immediately on start)
  useEffect(() => {
    if (trackingId === null) return;
    setPollCount(0);
    const tick = () => {
      const c = configsRef.current.find((x) => x.id === trackingId);
      if (c) resolveConfig(c, true); // background poll: don't toggle the Resolve/Track buttons
      setPollCount((n) => n + 1);
    };
    tick();
    const iv = setInterval(tick, Math.max(200, freq));
    return () => clearInterval(iv);
  }, [trackingId, freq, resolveConfig]);

  // react to deep-link from SideNav submenu
  useEffect(() => {
    if (editTarget) {
      openEditor(editTarget.kind, editTarget.id);
      onEditTargetApplied?.();
    }
  }, [editTarget, openEditor, onEditTargetApplied]);

  // main "Workflow" menu click -> leave the editor and show the tabs UI (skip initial mount)
  const homeKeyRef = useRef(homeKey);
  useEffect(() => {
    if (homeKey === homeKeyRef.current) return;
    homeKeyRef.current = homeKey;
    setEditor(null);
    setDetView(null);
    setTrackingId(null);
  }, [homeKey]);

  const refreshAndNotify = useCallback(async () => {
    await fetchAll();
    onInstancesChanged?.();
  }, [fetchAll, onInstancesChanged]);

  // ----- rows per tab -----
  // search (name/title) + status + time-range (updatedAt) filter.
  // Rows keep a stable order by id (NOT by updatedAt) so editing a row does not reorder the table.
  const q = activeSearch.trim();
  const st = filters.status.trim();
  const ids = filters.ids.trim();
  const tr = filters.timeRange;
  const keep = (id: number, name: string, title: string, status: string, ts: number) =>
    matchIds(id, ids) && matchText(`${name} ${title}`, q) && matchStatus(status, st) && isInTimeRange(ts, tr);
  const byIdAsc = <T extends { id: number }>(a: T, b: T) => a.id - b.id;

  // DetectorConfig/Detector have no direct oid/pid: oid == Contract.tenant_id, pid == Contract.project_id.
  const ownerOf = (d: DetectorConfig): { oid: string; pid: string } => ({
    oid: d.contract?.tenantId != null ? String(d.contract.tenantId) : '',
    pid: d.contract?.projectId != null ? String(d.contract.projectId) : '',
  });

  const rowsFor = (kind: EntityKind): TableRow[] => {
    switch (kind) {
      case KIND.workflowSchema: return schemas.filter((s) => keep(s.id, s.name, s.title, s.status, s.updatedAt)).sort(byIdAsc)
        .map((s) => ({ id: s.id, icon: s.icon, name: s.name, status: s.status, tags: s.tags, ts: s.updatedAt,
          cells: { title: s.title, graph: `${Object.keys(s.graph?.nodes ?? {}).length} ${t('workflow.graphNodes')}` } }));
      case KIND.workflowConfig: return configs.filter((c) => keep(c.id, c.name, c.title, c.status, c.updatedAt)).sort(byIdAsc)
        .map((c) => ({ id: c.id, icon: c.icon, name: c.name, oid: c.oid ?? '', pid: c.pid ?? '', status: c.status, tags: c.tags, ts: c.updatedAt,
          cells: { title: c.title, sid: String(c.sid), xid: c.xid ?? '' } }));
      case KIND.detectorSchema: return detSchemas.filter((d) => keep(d.id, d.name, d.title, d.status, d.updatedAt)).sort(byIdAsc)
        .map((d) => ({ id: d.id, icon: d.icon, name: d.name, status: d.status, tags: d.tags, ts: d.updatedAt,
          cells: { title: d.title, version: d.version } }));
      case KIND.detectorConfig: return detConfigs.filter((d) => keep(d.id, d.name, d.source ?? '', d.status, d.updatedAt)).sort(byIdAsc)
        .map((d) => ({ id: d.id, name: d.name, oid: ownerOf(d).oid, pid: ownerOf(d).pid, status: d.status, tags: d.tags, ts: d.updatedAt,
          cells: { source: d.source ?? '', config: d.config ? JSON.stringify(d.config) : '', version: d.schema?.version ?? '', schema: d.schema ? String(d.schema.id) : '' } }));
      // Detector: DetectorConfig enriched with its DetectorSchema (id/name/version/icon).
      // schema_icon (from the full DetectorSchema) is used for the row icon.
      case KIND.detector: return detConfigs.filter((d) => keep(d.id, d.name, d.source ?? '', d.status, d.updatedAt)).sort(byIdAsc)
        .map((d) => {
          const sid = d.schema?.id;
          const ds = sid != null ? detSchemas.find((s) => s.id === sid) : undefined;
          return {
            id: d.id, icon: ds?.icon, name: d.name, oid: ownerOf(d).oid, pid: ownerOf(d).pid, status: d.status, tags: d.tags, ts: d.updatedAt,
            cells: {
              source: d.source ?? '',
              config: d.config ? JSON.stringify(d.config) : '',
              schema_version: ds?.version ?? d.schema?.version ?? '',
              schema_id: sid != null ? String(sid) : '',
              schema_name: ds?.name ?? d.schema?.name ?? '',
            },
          };
        });
    }
  };

  const columnsFor = (kind: EntityKind): TableColumn[] => {
    const title = { key: 'title', label: t('workflow.fields.title') };
    switch (kind) {
      case KIND.workflowSchema: return [title, { key: 'graph', label: t('workflow.fields.graph'), width: 'w-32' }];
      // xid is a UUID -> give it room; shrink the title column to compensate
      case KIND.workflowConfig: return [
        { key: 'title', label: t('workflow.fields.title'), width: 'w-40' },
        { key: 'sid', label: t('workflow.fields.sid'), width: 'w-16' },
        { key: 'xid', label: 'xid', width: 'w-72' },
      ];
      case KIND.detectorSchema: return [title, { key: 'version', label: t('workflow.fields.version'), width: 'w-28' }];
      // source replaces title (kept compact); config shown truncated after source
      case KIND.detectorConfig: return [
        { key: 'source', label: t('workflow.fields.source'), width: 'w-40' },
        { key: 'config', label: t('workflow.fields.config') },
        { key: 'version', label: t('workflow.fields.version'), width: 'w-28' },
        { key: 'schema', label: t('workflow.fields.schema'), width: 'w-28' },
      ];
      // DetectorConfig columns + the schema_* columns (after config), from the enriched DetectorSchema
      case KIND.detector: return [
        { key: 'source', label: t('workflow.fields.source'), width: 'w-40' },
        { key: 'config', label: t('workflow.fields.config') },
        { key: 'schema_version', label: t('workflow.fields.schemaVersion'), width: 'w-32' },
        { key: 'schema_id', label: t('workflow.fields.schemaId'), width: 'w-24' },
        { key: 'schema_name', label: t('workflow.fields.schemaName'), width: 'w-40' },
      ];
    }
  };

  const defaultIconFor = (kind: EntityKind): string => {
    switch (kind) {
      case KIND.workflowSchema: return DEFAULT_WF_SCHEMA_ICON;
      case KIND.workflowConfig: return DEFAULT_WF_CONFIG_ICON;
      case KIND.detectorSchema: return DEFAULT_SCHEMA_ICON;
      case KIND.detectorConfig: return DEFAULT_CONFIG_ICON;
      case KIND.detector: return DEFAULT_SCHEMA_ICON; // icon column shows the DetectorSchema icon
    }
  };

  // The "Detector" tab is a read-only enriched view over DetectorConfig: slider / add / delete all
  // operate on the DetectorConfig entity (full code reuse), so map detector -> detectorConfig.
  const effectiveKind = (kind: EntityKind): EntityKind => (kind === KIND.detector ? KIND.detectorConfig : kind);

  // ----- open details slider for a row -----
  const openDetails = (kind: EntityKind, id: number) => { setSliderKind(kind); setSelectedId(id); setAddMode(false); };
  const openAdd = (kind: EntityKind) => { setSliderKind(kind); setSelectedId(null); setAddMode(true); };
  const closeSlider = () => { setSliderKind(null); setSelectedId(null); setAddMode(false); };

  // ----- delete from table -----
  const handleDelete = async (kind: EntityKind, id: number) => {
    if (!window.confirm(t('workflow.confirmDelete', { id }))) return;
    try {
      if (kind === KIND.workflowSchema) await api.deleteSchema(token, id);
      else if (kind === KIND.workflowConfig) await api.deleteConfig(token, id);
      else if (kind === KIND.detectorSchema) await api.deleteDetectorSchema(token, id);
      else await api.deleteDetectorConfig(token, id);
      await refreshAndNotify();
    } catch (e) {
      notifyError(t('workflow.errorDelete'), e instanceof Error ? e.message : String(e));
    }
  };

  // ----- editor save -----
  // Persist the graf but STAY in the editor (don't reset the view). The editor keeps its own
  // react-flow state (nodes/edges/viewport) since it is not remounted (same key, editor != null),
  // so refreshing the table data below does not reset what the user is looking at.
  const handleEditorSave = async (graf: WorkflowGraf) => {
    if (!editor) return;
    setSaving(true);
    try {
      if (editor.kind === KIND.workflowSchema) await api.updateSchema(token, editor.id, { graph: graf });
      else await api.updateConfig(token, editor.id, { graph: graf });
      // keep the editor's captured graf in sync with what was just saved (used only on remount)
      setEditor((e) => (e ? { ...e, graf } : e));
      await refreshAndNotify();
    } catch (e) {
      notifyError(t('workflow.errorSave'), e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  // Delete the WorkflowSchema/WorkflowConfig currently open in the editor (asks confirmation),
  // then exit the editor and refresh the lists.
  const destroyEditorEntity = async () => {
    if (!editor) return;
    if (!window.confirm(t('workflow.confirmDelete', { id: editor.id }))) return;
    setSaving(true);
    try {
      if (editor.kind === KIND.workflowSchema) await api.deleteSchema(token, editor.id);
      else await api.deleteConfig(token, editor.id);
      setTrackingId(null); setEditor(null);
      onActiveInstanceChange?.(null); // deleted -> clear the SideNav submenu highlight
      await refreshAndNotify();
    } catch (e) {
      notifyError(t('workflow.errorDelete'), e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  // Stop (terminate) / Cancel the editor's WorkflowConfig run on the Engine, then reflect the new status.
  const stopOrCancelEditorConfig = async (mode: 'stop' | 'cancel') => {
    if (!editor || editor.kind !== KIND.workflowConfig) return;
    if (!window.confirm(t(mode === 'stop' ? 'workflow.confirmStop' : 'workflow.confirmCancel', { id: editor.id }))) return;
    setSaving(true);
    try {
      const c = mode === 'stop' ? await api.stopConfig(token, editor.id) : await api.cancelConfig(token, editor.id);
      setEditor((e) => (e ? { ...e, status: c.status } : e));
      await refreshAndNotify();
    } catch (e) {
      notifyError(t(mode === 'stop' ? 'workflow.errorStop' : 'workflow.errorCancel'), e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  // ===================== EDITOR MODE =====================
  if (editor) {
    const editorSchema = editor.kind === KIND.workflowSchema ? schemas.find((s) => s.id === editor.id) ?? null : null;
    const editorConfig = editor.kind === KIND.workflowConfig ? configs.find((c) => c.id === editor.id) ?? null : null;
    return (
      <>
        <WorkflowEditor
          key={`${editor.kind}-${editor.id}`}
          id={editor.id}
          title={editor.title}
          name={editor.name}
          icon={editor.icon}
          kind={editor.kind}
          graf={editor.graf}
          detectorSchemas={detSchemas}
          detectorConfigs={detConfigs}
          saving={saving}
          status={editor.status}
          xid={editor.xid}
          engineUrl={editor.engineUrl}
          engine={editor.engine}
          detectorStatus={resolvedDetStatus}
          detectorActivityId={resolvedDetActivity}
          resolving={resolving}
          onResolve={editorConfig ? () => resolveConfig(editorConfig) : undefined}
          onValidateCid={validateCid}
          tracking={trackingId === editor.id}
          pollCount={pollCount}
          freq={freq}
          onFreqChange={setFreq}
          onToggleTrack={editorConfig ? () => setTrackingId((cur) => (cur === editor.id ? null : editor.id)) : undefined}
          onSave={handleEditorSave}
          onCreateConfig={editor.kind === KIND.workflowSchema ? () => { setStartSchemaId(editor.id); setStartOpen(true); } : undefined}
          onStart={editor.kind === KIND.workflowSchema ? () => { setStartSchemaId(editor.id); setStartOpen(true); } : undefined}
          onStop={editorConfig ? () => stopOrCancelEditorConfig('stop') : undefined}
          onCancel={editorConfig ? () => stopOrCancelEditorConfig('cancel') : undefined}
          onDestroy={destroyEditorEntity}
          onBack={() => { setTrackingId(null); setEditor(null); }}
          onOpenDetails={() => setWfDetailsOpen(true)}
          onOpenDetectorSchema={(id) => setDetView({ kind: KIND.detectorSchema, id })}
          onOpenDetectorConfig={(id) => setDetView({ kind: KIND.detectorConfig, id })}
        />
        {/* same editable Detail component as the table view, opened from the node's sid/cid [->] */}
        <DetectorSlider
          open={detView !== null}
          addMode={false}
          kind={detView?.kind ?? KIND.detectorSchema}
          schema={detView?.kind === KIND.detectorSchema ? detSchemas.find((d) => d.id === detView.id) ?? null : null}
          config={detView?.kind === KIND.detectorConfig ? detConfigs.find((d) => d.id === detView.id) ?? null : null}
          schemas={detSchemas}
          saving={saving}
          timezone={timezone}
          onClose={() => setDetView(null)}
          onCreateSchema={async () => {}}
          onCreateConfig={async () => {}}
          onUpdateSchema={async (patch) => { if (!detView) return; setSaving(true); try { await api.updateDetectorSchema(token, detView.id, patch); await fetchAll(); setDetView(null); } finally { setSaving(false); } }}
          onUpdateConfig={async (patch) => { if (!detView) return; setSaving(true); try { await api.updateDetectorConfig(token, detView.id, patch); await fetchAll(); setDetView(null); } finally { setSaving(false); } }}
          onDelete={async () => {
            if (!detView) return;
            setSaving(true);
            try {
              if (detView.kind === KIND.detectorSchema) await api.deleteDetectorSchema(token, detView.id);
              else await api.deleteDetectorConfig(token, detView.id);
              await fetchAll(); setDetView(null);
            } finally { setSaving(false); }
          }}
        />
        {/* Edit the WorkflowSchema/Config metadata from the editor Panel 1 [->] */}
        <WorkflowSlider
          open={wfDetailsOpen}
          addMode={false}
          kind={editor.kind}
          schema={editorSchema}
          config={editorConfig}
          schemas={schemas}
          saving={saving}
          timezone={timezone}
          onClose={() => setWfDetailsOpen(false)}
          onCreateSchema={async () => {}}
          onCreateConfig={async () => {}}
          onUpdate={async (patch) => {
            setSaving(true);
            try {
              if (editor.kind === KIND.workflowSchema) {
                await api.updateSchema(token, editor.id, patch);
                const v = await api.getSchema(token, editor.id);
                setEditor((e) => e ? { ...e, title: v.schema.title, name: v.schema.name, icon: v.schema.icon } : e);
              } else {
                await api.updateConfig(token, editor.id, patch);
                const v = await api.getConfig(token, editor.id);
                setEditor((e) => e ? { ...e, title: v.config.title, name: v.config.name, icon: v.config.icon } : e);
              }
              await refreshAndNotify();
              setWfDetailsOpen(false);
            } finally { setSaving(false); }
          }}
          onDelete={async () => {
            setSaving(true);
            try {
              if (editor.kind === KIND.workflowSchema) await api.deleteSchema(token, editor.id);
              else await api.deleteConfig(token, editor.id);
              await refreshAndNotify();
              setWfDetailsOpen(false);
              setEditor(null);
            } finally { setSaving(false); }
          }}
          onEdit={() => setWfDetailsOpen(false)}
        />
        {/* Start a workflow from this WorkflowSchema (opened from the editor [Start] button).
            Pre-fill tq / ns / input from the schema's meta when present. */}
        {(() => {
          const startSchema = startSchemaId != null ? schemas.find((s) => s.id === startSchemaId) : undefined;
          return (
            <SchemaStartDialog
              open={startOpen}
              schemaId={startSchemaId ?? undefined}
              schemaName={startSchema?.name ?? editor.name}
              schema={startSchema?.schema}
              uiSchema={startSchema?.uiSchema}
              defaultTaskQueue={startSchema?.meta?.tq != null ? String(startSchema.meta.tq) : undefined}
              defaultNs={startSchema?.meta?.ns != null ? String(startSchema.meta.ns) : undefined}
              defaultInput={metaInputText(startSchema?.meta)}
              defaultInputData={metaInputDataText(startSchema?.meta)}
              defaultOid={defaultOid}
              saving={saving}
              onClose={() => setStartOpen(false)}
              onStart={startFromSchema}
              onCreate={spawnFromSchema}
            />
          );
        })()}
      </>
    );
  }

  // ===================== TABLE / TABS MODE =====================
  const selectedSchema = sliderKind === KIND.workflowSchema && selectedId !== null ? schemas.find((s) => s.id === selectedId) ?? null : null;
  const selectedConfig = sliderKind === KIND.workflowConfig && selectedId !== null ? configs.find((c) => c.id === selectedId) ?? null : null;
  const selectedDetSchema = sliderKind === KIND.detectorSchema && selectedId !== null ? detSchemas.find((d) => d.id === selectedId) ?? null : null;
  const selectedDetConfig = (sliderKind === KIND.detectorConfig || sliderKind === KIND.detector) && selectedId !== null ? detConfigs.find((d) => d.id === selectedId) ?? null : null;

  return (
    <>
      <ModulePage
        title={t('nav.workflow')}
        tabs={TAB_IDS.map((id) => ({ id, label: t(entityLabelKey(id)) }))}
        padded={false}
        onTabChange={() => setPage(1)}
        afterTabs={(active) => (
          <WorkflowFilters
            filters={filters}
            timezone={timezone}
            onFilterChange={handleFilterChange}
            onTimezoneChange={setTimezone}
            onSearch={handleSearch}
            onAdd={() => openAdd(effectiveKind(active as EntityKind))}
            onRefresh={refreshAndNotify}
            onResolve={active === KIND.workflowConfig ? resolveAllConfigs : undefined}
            resolving={resolving}
          />
        )}
      >
        {(active) => {
          const allRows = rowsFor(active as EntityKind);
          const total = allRows.length;
          const pageRows = pageSize === PAGE_SIZE_ALL ? allRows : allRows.slice((page - 1) * pageSize, page * pageSize);
          return (
            <div className="flex flex-col h-full">
              <div className="flex-1 overflow-auto bg-card">
                <EntityTable
                  rows={pageRows}
                  columns={columnsFor(active as EntityKind)}
                  defaultIcon={defaultIconFor(active as EntityKind)}
                  timezone={timezone}
                  showOwner={active === KIND.workflowConfig || active === KIND.detectorConfig || active === KIND.detector}
                  selectedId={sliderKind === (active as EntityKind) ? selectedId : null}
                  minRows={pageSize === PAGE_SIZE_ALL ? pageRows.length : pageSize}
                  onRowClick={(id) => openDetails(active as EntityKind, id)}
                  onRowDoubleClick={(id) => {
                    const k = active as EntityKind;
                    // double-click a WorkflowSchema/Config -> go straight to the design (editor) view
                    if (k === KIND.workflowSchema || k === KIND.workflowConfig) { closeSlider(); openEditor(k, id); }
                  }}
                  onDelete={(id) => handleDelete(effectiveKind(active as EntityKind), id)}
                />
              </div>
              <Pagination
                page={page}
                pageSize={pageSize}
                total={total}
                onPageChange={setPage}
                onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
                footerLeft={<span>{total} {t(entityLabelKey(active as EntityKind))}</span>}
              />
            </div>
          );
        }}
      </ModulePage>

      {/* Workflow schema/config details */}
      <WorkflowSlider
        open={sliderKind === KIND.workflowSchema || sliderKind === KIND.workflowConfig}
        addMode={addMode}
        kind={sliderKind === KIND.workflowConfig ? KIND.workflowConfig : KIND.workflowSchema}
        schema={selectedSchema}
        config={selectedConfig}
        schemas={schemas}
        saving={saving}
        timezone={timezone}
        onClose={closeSlider}
        onCreateSchema={async (name, title, description, version, icon, tags, schema, uiSchema) => {
          setSaving(true);
          try { await api.createSchema(token, { name, title, description, version, icon, tags, schema, uiSchema }); await refreshAndNotify(); closeSlider(); }
          finally { setSaving(false); }
        }}
        onCreateConfig={async (sid, name, oid, pid, xid) => {
          setSaving(true);
          try { await api.createConfig(token, { sid, name, oid, pid, xid }); await refreshAndNotify(); closeSlider(); }
          finally { setSaving(false); }
        }}
        onUpdate={async (patch) => {
          if (selectedId === null) return;
          setSaving(true);
          try {
            if (sliderKind === KIND.workflowSchema) await api.updateSchema(token, selectedId, patch);
            else await api.updateConfig(token, selectedId, patch);
            await refreshAndNotify(); closeSlider();
          } finally { setSaving(false); }
        }}
        onDelete={async () => {
          if (selectedId === null) return;
          setSaving(true);
          try {
            if (sliderKind === KIND.workflowSchema) await api.deleteSchema(token, selectedId);
            else await api.deleteConfig(token, selectedId);
            await refreshAndNotify(); closeSlider();
          } finally { setSaving(false); }
        }}
        onEdit={() => { if (selectedId !== null) openEditor(sliderKind === KIND.workflowConfig ? KIND.workflowConfig : KIND.workflowSchema, selectedId); closeSlider(); }}
        resolving={resolving}
        onResolve={selectedConfig ? () => resolveConfig(selectedConfig) : undefined}
      />

      {/* Detector schema/config details */}
      <DetectorSlider
        open={sliderKind === KIND.detectorSchema || sliderKind === KIND.detectorConfig || sliderKind === KIND.detector}
        addMode={addMode}
        kind={sliderKind === KIND.detectorSchema ? KIND.detectorSchema : KIND.detectorConfig}
        extended={sliderKind === KIND.detector}
        schema={selectedDetSchema}
        config={selectedDetConfig}
        schemas={detSchemas}
        saving={saving}
        timezone={timezone}
        onClose={closeSlider}
        onCreateSchema={async (req) => { setSaving(true); try { await api.createDetectorSchema(token, req); await fetchAll(); closeSlider(); } finally { setSaving(false); } }}
        onCreateConfig={async (req) => { setSaving(true); try { await api.createDetectorConfig(token, req); await fetchAll(); closeSlider(); } finally { setSaving(false); } }}
        onUpdateSchema={async (patch) => { if (selectedId === null) return; setSaving(true); try { await api.updateDetectorSchema(token, selectedId, patch); await fetchAll(); closeSlider(); } finally { setSaving(false); } }}
        onUpdateConfig={async (patch) => { if (selectedId === null) return; setSaving(true); try { await api.updateDetectorConfig(token, selectedId, patch); await fetchAll(); closeSlider(); } finally { setSaving(false); } }}
        onOpenSchema={(id) => openDetails(KIND.detectorSchema, id)}
        onDelete={async () => {
          if (selectedId === null) return;
          setSaving(true);
          try {
            if (sliderKind === KIND.detectorSchema) await api.deleteDetectorSchema(token, selectedId);
            else await api.deleteDetectorConfig(token, selectedId);
            await fetchAll(); closeSlider();
          } finally { setSaving(false); }
        }}
      />
    </>
  );
}

function mergeDetectors(cur: DetectorSchema[], detectors?: Record<string, DetectorSchema>): DetectorSchema[] {
  if (!detectors) return cur;
  const byId = new Map(cur.map((d) => [d.id, d]));
  for (const d of Object.values(detectors)) byId.set(d.id, d);
  return Array.from(byId.values());
}
