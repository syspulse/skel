import React, { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ModulePage } from '../components/ModulePage';
import { useAuth } from '../auth/useAuth';
import { useModuleNotify } from '../notifications/moduleNotify';
import { IconRefresh, IconPlus } from '../components/Icons';
import * as api from './api';
import type {
  WorkflowSchema, WorkflowConfig, DetectorSchema, DetectorConfig, WorkflowGraf, EntityKind,
} from './types';
import { EntityTable, type TableRow } from './components/EntityTable';
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
  { id: 'schema', key: 'workflow.tabs.schema' },
  { id: 'config', key: 'workflow.tabs.config' },
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

export function WorkflowPage({ editTarget, homeKey, onEditTargetApplied, onInstancesChanged }: WorkflowPageProps) {
  const { t } = useTranslation();
  const { token } = useAuth();
  const { notifyError } = useModuleNotify(t('nav.workflow'));

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
  const rowsFor = (kind: EntityKind): TableRow[] => {
    switch (kind) {
      case 'schema': return schemas.map((s) => ({ id: s.id, icon: s.icon, name: s.name, title: s.title, status: s.status, tags: s.tags, extra: `${t('workflow.graphNodes')}: ${Object.keys(s.graph?.nodes ?? {}).length}` }));
      case 'config': return configs.map((c) => ({ id: c.id, icon: c.icon, name: c.name, title: c.title, status: c.status, tags: c.tags, extra: `sid ${c.sid}` }));
      case 'detector-schema': return detSchemas.map((d) => ({ id: d.id, icon: d.icon, name: d.name, title: d.title, status: d.status, tags: d.tags, extra: d.version }));
      case 'detector-config': return detConfigs.map((d) => ({ id: d.id, name: d.name, status: d.status, tags: d.tags, extra: d.schema ? `${t('workflow.fields.schema')} #${d.schema.id}` : '' }));
    }
  };

  const extraLabelFor = (kind: EntityKind): string => {
    switch (kind) {
      case 'schema': return t('workflow.fields.graph');
      case 'config': return t('workflow.fields.sid');
      case 'detector-schema': return t('workflow.fields.version');
      case 'detector-config': return t('workflow.fields.schema');
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
    return (
      <>
        <WorkflowEditor
          key={`${editor.kind}-${editor.id}`}
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
          onClose={() => setDetView(null)}
          onCreateSchema={async () => {}}
          onCreateConfig={async () => {}}
          onUpdateConfig={async () => {}}
          onDelete={async () => {}}
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
        afterTabs={(active) => (
          <div className="flex items-center gap-3 px-4 py-2 bg-card border-b border-border">
            <span className="text-xs text-muted-foreground">{rowsFor(active as EntityKind).length} {t(`workflow.tabs.${toKey(active as EntityKind)}`)}</span>
            <div className="flex-1" />
            <button onClick={refreshAndNotify}
              className="inline-flex items-center gap-1.5 text-xs bg-muted hover:bg-muted-hover text-foreground px-3 py-1 rounded border border-border transition-colors">
              <IconRefresh size={14} /> {t('common.refresh')}
            </button>
            <button onClick={() => openAdd(active as EntityKind)}
              className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 transition-colors">
              <IconPlus size={13} /> {t('common.add')}
            </button>
          </div>
        )}
      >
        {(active) => (
          <div className="h-full overflow-auto px-4 pb-3">
            <EntityTable
              rows={rowsFor(active as EntityKind)}
              selectedId={sliderKind === active ? selectedId : null}
              extraLabel={extraLabelFor(active as EntityKind)}
              onRowClick={(id) => openDetails(active as EntityKind, id)}
              onDelete={(id) => handleDelete(active as EntityKind, id)}
            />
          </div>
        )}
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
