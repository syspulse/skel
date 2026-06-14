import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ModulePage } from '../components/ModulePage';
import { useAuth } from '../auth/useAuth';
import { useModuleNotify } from '../notifications/moduleNotify';
import { usePageSize, PAGE_SIZE_ALL } from '../settings/PageSizeContext';
import { Pagination } from '../components/Pagination';
import * as api from './api';
import type {
  WorkflowSchema, WorkflowConfig, DetectorSchema, DetectorConfig, WorkflowGraf, EntityKind,
} from './types';
import { EntityTable, type TableRow, type TableColumn } from './components/EntityTable';
import { WorkflowFilters, type WorkflowFilterState } from './components/WorkflowFilters';
import type { TimeRange } from '../types';
import { DEFAULT_SCHEMA_ICON, DEFAULT_CONFIG_ICON, DEFAULT_WF_SCHEMA_ICON, DEFAULT_WF_CONFIG_ICON } from './editor/IconPicker';
import { WorkflowSlider } from './components/WorkflowSlider';
import { DetectorSlider } from './components/DetectorSlider';
import { WorkflowEditor } from './editor/WorkflowEditor';

export interface WorkflowEditTarget { kind: 'schema' | 'config'; id: number; }

interface WorkflowPageProps {
  /** Deep-link from the SideNav submenu: open the editor for this instance. */
  editTarget?: WorkflowEditTarget | null;
  /** Bumped when the main "Workflow" menu is clicked: exit the editor and show the tabs UI. */
  homeKey?: number;
  onEditTargetApplied?: () => void;
  /** Notify the SideNav that instances changed (so it can refresh submenus). */
  onInstancesChanged?: () => void;
}

const TABS: { id: EntityKind; key: string }[] = [
  { id: 'schema', key: 'workflow.tabs.workflowSchema' },
  { id: 'config', key: 'workflow.tabs.workflowConfig' },
  { id: 'detector-schema', key: 'workflow.tabs.detectorSchema' },
  { id: 'detector-config', key: 'workflow.tabs.detectorConfig' },
];

interface EditorState {
  kind: 'schema' | 'config';
  id: number;
  title: string;
  name: string;
  icon?: string;
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

export function WorkflowPage({ editTarget, homeKey, onEditTargetApplied, onInstancesChanged }: WorkflowPageProps) {
  const { t } = useTranslation();
  const { token } = useAuth();
  const { notifyError } = useModuleNotify(t('nav.workflow'));
  const { pageSize, setPageSize } = usePageSize();
  const [page, setPage] = useState(1);
  const [timezone, setTimezone] = useState('local');
  const [activeSearch, setActiveSearch] = useState('');
  const [filters, setFilters] = useState<WorkflowFilterState>({ search: '', status: '', timeRange: { type: 'all' } });

  const handleFilterChange = (f: WorkflowFilterState) => {
    if (f.status !== filters.status || f.timeRange !== filters.timeRange) setPage(1);
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

  const [editor, setEditor] = useState<EditorState | null>(null);
  // detector detail opened from the editor (sid/cid [->]) - read-only overlay
  const [detView, setDetView] = useState<{ kind: 'detector-schema' | 'detector-config'; id: number } | null>(null);
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
  const openEditor = useCallback(async (kind: 'schema' | 'config', id: number) => {
    try {
      if (kind === 'schema') {
        const v = await api.getSchema(token, id, true);
        setDetSchemas((cur) => mergeDetectors(cur, v.detectors));
        setEditor({ kind, id, title: v.schema.title, name: v.schema.name, icon: v.schema.icon, graf: v.schema.graph });
      } else {
        const v = await api.getConfig(token, id, true);
        setEditor({ kind, id, title: v.config.title, name: v.config.name, icon: v.config.icon, graf: v.config.graph });
      }
    } catch (e) {
      notifyError(t('workflow.errorLoad'), e instanceof Error ? e.message : String(e));
    }
  }, [token, notifyError, t]);

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
  }, [homeKey]);

  const refreshAndNotify = useCallback(async () => {
    await fetchAll();
    onInstancesChanged?.();
  }, [fetchAll, onInstancesChanged]);

  // ----- rows per tab -----
  // search (name/title) + status + time-range (updatedAt) filter, newest first
  const q = activeSearch.trim();
  const st = filters.status.trim();
  const tr = filters.timeRange;
  const keep = (name: string, title: string, status: string, ts: number) =>
    matchText(`${name} ${title}`, q) && matchStatus(status, st) && isInTimeRange(ts, tr);
  const byTsDesc = <T extends { updatedAt: number }>(a: T, b: T) => b.updatedAt - a.updatedAt;

  const rowsFor = (kind: EntityKind): TableRow[] => {
    switch (kind) {
      case 'schema': return schemas.filter((s) => keep(s.name, s.title, s.status, s.updatedAt)).sort(byTsDesc)
        .map((s) => ({ id: s.id, icon: s.icon, name: s.name, status: s.status, tags: s.tags, ts: s.updatedAt,
          cells: { title: s.title, graph: `${Object.keys(s.graph?.nodes ?? {}).length} ${t('workflow.graphNodes')}` } }));
      case 'config': return configs.filter((c) => keep(c.name, c.title, c.status, c.updatedAt)).sort(byTsDesc)
        .map((c) => ({ id: c.id, icon: c.icon, name: c.name, status: c.status, tags: c.tags, ts: c.updatedAt,
          cells: { title: c.title, sid: String(c.sid) } }));
      case 'detector-schema': return detSchemas.filter((d) => keep(d.name, d.title, d.status, d.updatedAt)).sort(byTsDesc)
        .map((d) => ({ id: d.id, icon: d.icon, name: d.name, status: d.status, tags: d.tags, ts: d.updatedAt,
          cells: { title: d.title, version: d.version } }));
      case 'detector-config': return detConfigs.filter((d) => keep(d.name, d.contract?.name ?? '', d.status, d.updatedAt)).sort(byTsDesc)
        .map((d) => ({ id: d.id, name: d.name, status: d.status, tags: d.tags, ts: d.updatedAt,
          cells: { title: d.contract?.name ?? '', version: d.schema?.version ?? '', schema: d.schema ? String(d.schema.id) : '' } }));
    }
  };

  const columnsFor = (kind: EntityKind): TableColumn[] => {
    const title = { key: 'title', label: t('workflow.fields.title') };
    switch (kind) {
      case 'schema': return [title, { key: 'graph', label: t('workflow.fields.graph'), width: 'w-32' }];
      case 'config': return [title, { key: 'sid', label: t('workflow.fields.sid'), width: 'w-24' }];
      case 'detector-schema': return [title, { key: 'version', label: t('workflow.fields.version'), width: 'w-28' }];
      case 'detector-config': return [
        title,
        { key: 'version', label: t('workflow.fields.version'), width: 'w-28' },
        { key: 'schema', label: t('workflow.fields.schema'), width: 'w-28' },
      ];
    }
  };

  const defaultIconFor = (kind: EntityKind): string => {
    switch (kind) {
      case 'schema': return DEFAULT_WF_SCHEMA_ICON;
      case 'config': return DEFAULT_WF_CONFIG_ICON;
      case 'detector-schema': return DEFAULT_SCHEMA_ICON;
      case 'detector-config': return DEFAULT_CONFIG_ICON;
    }
  };

  // ----- open details slider for a row -----
  const openDetails = (kind: EntityKind, id: number) => { setSliderKind(kind); setSelectedId(id); setAddMode(false); };
  const openAdd = (kind: EntityKind) => { setSliderKind(kind); setSelectedId(null); setAddMode(true); };
  const closeSlider = () => { setSliderKind(null); setSelectedId(null); setAddMode(false); };

  // ----- delete from table -----
  const handleDelete = async (kind: EntityKind, id: number) => {
    if (!window.confirm(t('workflow.confirmDelete', { id }))) return;
    try {
      if (kind === 'schema') await api.deleteSchema(token, id);
      else if (kind === 'config') await api.deleteConfig(token, id);
      else if (kind === 'detector-schema') await api.deleteDetectorSchema(token, id);
      else await api.deleteDetectorConfig(token, id);
      await refreshAndNotify();
    } catch (e) {
      notifyError(t('workflow.errorDelete'), e instanceof Error ? e.message : String(e));
    }
  };

  // ----- editor save -----
  const handleEditorSave = async (graf: WorkflowGraf) => {
    if (!editor) return;
    setSaving(true);
    try {
      if (editor.kind === 'schema') await api.updateSchema(token, editor.id, { graph: graf });
      else await api.updateConfig(token, editor.id, { graph: graf });
      await refreshAndNotify();
      setEditor(null);
    } catch (e) {
      notifyError(t('workflow.errorSave'), e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  // ===================== EDITOR MODE =====================
  if (editor) {
    const editorSchema = editor.kind === 'schema' ? schemas.find((s) => s.id === editor.id) ?? null : null;
    const editorConfig = editor.kind === 'config' ? configs.find((c) => c.id === editor.id) ?? null : null;
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
          onSave={handleEditorSave}
          onBack={() => setEditor(null)}
          onOpenDetails={() => setWfDetailsOpen(true)}
          onOpenDetectorSchema={(id) => setDetView({ kind: 'detector-schema', id })}
          onOpenDetectorConfig={(id) => setDetView({ kind: 'detector-config', id })}
        />
        <DetectorSlider
          open={detView !== null}
          addMode={false}
          readOnly
          kind={detView?.kind ?? 'detector-schema'}
          schema={detView?.kind === 'detector-schema' ? detSchemas.find((d) => d.id === detView.id) ?? null : null}
          config={detView?.kind === 'detector-config' ? detConfigs.find((d) => d.id === detView.id) ?? null : null}
          schemas={detSchemas}
          saving={false}
          timezone={timezone}
          onClose={() => setDetView(null)}
          onCreateSchema={async () => {}}
          onCreateConfig={async () => {}}
          onUpdateConfig={async () => {}}
          onDelete={async () => {}}
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
              if (editor.kind === 'schema') {
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
              if (editor.kind === 'schema') await api.deleteSchema(token, editor.id);
              else await api.deleteConfig(token, editor.id);
              await refreshAndNotify();
              setWfDetailsOpen(false);
              setEditor(null);
            } finally { setSaving(false); }
          }}
          onEdit={() => setWfDetailsOpen(false)}
        />
      </>
    );
  }

  // ===================== TABLE / TABS MODE =====================
  const selectedSchema = sliderKind === 'schema' && selectedId !== null ? schemas.find((s) => s.id === selectedId) ?? null : null;
  const selectedConfig = sliderKind === 'config' && selectedId !== null ? configs.find((c) => c.id === selectedId) ?? null : null;
  const selectedDetSchema = sliderKind === 'detector-schema' && selectedId !== null ? detSchemas.find((d) => d.id === selectedId) ?? null : null;
  const selectedDetConfig = sliderKind === 'detector-config' && selectedId !== null ? detConfigs.find((d) => d.id === selectedId) ?? null : null;

  return (
    <>
      <ModulePage
        title={t('nav.workflow')}
        tabs={TABS.map((tab) => ({ id: tab.id, label: t(tab.key) }))}
        padded={false}
        onTabChange={() => setPage(1)}
        afterTabs={(active) => (
          <WorkflowFilters
            filters={filters}
            timezone={timezone}
            onFilterChange={handleFilterChange}
            onTimezoneChange={setTimezone}
            onSearch={handleSearch}
            onAdd={() => openAdd(active as EntityKind)}
            onRefresh={refreshAndNotify}
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
                  selectedId={sliderKind === active ? selectedId : null}
                  minRows={pageSize === PAGE_SIZE_ALL ? pageRows.length : pageSize}
                  onRowClick={(id) => openDetails(active as EntityKind, id)}
                  onDelete={(id) => handleDelete(active as EntityKind, id)}
                />
              </div>
              <Pagination
                page={page}
                pageSize={pageSize}
                total={total}
                onPageChange={setPage}
                onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
                footerLeft={<span>{total} {t(`workflow.tabs.${toKey(active as EntityKind)}`)}</span>}
              />
            </div>
          );
        }}
      </ModulePage>

      {/* Workflow schema/config details */}
      <WorkflowSlider
        open={sliderKind === 'schema' || sliderKind === 'config'}
        addMode={addMode}
        kind={sliderKind === 'config' ? 'config' : 'schema'}
        schema={selectedSchema}
        config={selectedConfig}
        schemas={schemas}
        saving={saving}
        timezone={timezone}
        onClose={closeSlider}
        onCreateSchema={async (name, title, description, version, icon, tags) => {
          setSaving(true);
          try { await api.createSchema(token, { name, title, description, version, icon, tags }); await refreshAndNotify(); closeSlider(); }
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
            if (sliderKind === 'schema') await api.updateSchema(token, selectedId, patch);
            else await api.updateConfig(token, selectedId, patch);
            await refreshAndNotify(); closeSlider();
          } finally { setSaving(false); }
        }}
        onDelete={async () => {
          if (selectedId === null) return;
          setSaving(true);
          try {
            if (sliderKind === 'schema') await api.deleteSchema(token, selectedId);
            else await api.deleteConfig(token, selectedId);
            await refreshAndNotify(); closeSlider();
          } finally { setSaving(false); }
        }}
        onEdit={() => { if (selectedId !== null) openEditor(sliderKind === 'config' ? 'config' : 'schema', selectedId); closeSlider(); }}
      />

      {/* Detector schema/config details */}
      <DetectorSlider
        open={sliderKind === 'detector-schema' || sliderKind === 'detector-config'}
        addMode={addMode}
        kind={sliderKind === 'detector-config' ? 'detector-config' : 'detector-schema'}
        schema={selectedDetSchema}
        config={selectedDetConfig}
        schemas={detSchemas}
        saving={saving}
        timezone={timezone}
        onClose={closeSlider}
        onCreateSchema={async (req) => { setSaving(true); try { await api.createDetectorSchema(token, req); await fetchAll(); closeSlider(); } finally { setSaving(false); } }}
        onCreateConfig={async (req) => { setSaving(true); try { await api.createDetectorConfig(token, req); await fetchAll(); closeSlider(); } finally { setSaving(false); } }}
        onUpdateConfig={async (patch) => { if (selectedId === null) return; setSaving(true); try { await api.updateDetectorConfig(token, selectedId, patch); await fetchAll(); closeSlider(); } finally { setSaving(false); } }}
        onDelete={async () => {
          if (selectedId === null) return;
          setSaving(true);
          try {
            if (sliderKind === 'detector-schema') await api.deleteDetectorSchema(token, selectedId);
            else await api.deleteDetectorConfig(token, selectedId);
            await fetchAll(); closeSlider();
          } finally { setSaving(false); }
        }}
      />
    </>
  );
}

function toKey(kind: EntityKind): string {
  return kind === 'schema' ? 'schema' : kind === 'config' ? 'config' : kind === 'detector-schema' ? 'detectorSchema' : 'detectorConfig';
}

function mergeDetectors(cur: DetectorSchema[], detectors?: Record<string, DetectorSchema>): DetectorSchema[] {
  if (!detectors) return cur;
  const byId = new Map(cur.map((d) => [d.id, d]));
  for (const d of Object.values(detectors)) byId.set(d.id, d);
  return Array.from(byId.values());
}
