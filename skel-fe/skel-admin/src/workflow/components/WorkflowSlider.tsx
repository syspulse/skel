import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { WorkflowSchema, WorkflowConfig } from '../types';
import { entityLabelKey } from '../types';
import { IconClose, IconSave, IconTrash, IconEdit } from '../../components/Icons';
import { IconPicker } from '../editor/IconPicker';
import { FormattedTimestamp } from '../../components/FormattedTimestamp';
import { TagsInput } from '../../components/TagsInput';

const STATUSES = ['ACTIVE', 'DISABLED', 'DELETED'];

interface CommonForm {
  name: string; title: string; description: string; status: string;
  version: string; icon?: string; tags: string;
  oid: string; pid: string; xid: string; sid?: number;
}

function emptyForm(): CommonForm {
  return { name: '', title: '', description: '', status: 'ACTIVE', version: '1.0.0', icon: undefined, tags: '', oid: '', pid: '', xid: '' };
}

interface WorkflowSliderProps {
  open: boolean;
  addMode: boolean;
  kind: 'schema' | 'config';
  schema: WorkflowSchema | null;
  config: WorkflowConfig | null;
  schemas: WorkflowSchema[];   // for config create (choose source schema)
  saving: boolean;
  timezone: string;
  onClose: () => void;
  onCreateSchema: (name: string, title?: string, description?: string, version?: string, icon?: string, tags?: string[]) => Promise<void>;
  onCreateConfig: (sid: number, name?: string, oid?: string, pid?: string, xid?: string) => Promise<void>;
  onUpdate: (patch: Record<string, unknown>) => Promise<void>;
  onDelete: () => Promise<void>;
  onEdit: () => void;          // open WorkflowGraf editor
}

const inputCls = 'flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400';
const roCls = 'flex-1 text-sm bg-muted border border-border rounded px-3 py-1 text-foreground select-text';

export function WorkflowSlider(props: WorkflowSliderProps) {
  const { open, addMode, kind, schema, config, schemas, saving, timezone, onClose, onCreateSchema, onCreateConfig, onUpdate, onDelete, onEdit } = props;
  const { t } = useTranslation();
  const [form, setForm] = useState<CommonForm>(emptyForm());
  const [sid, setSid] = useState<number | ''>('');
  const [error, setError] = useState<string | null>(null);

  const entity = kind === 'schema' ? schema : config;
  const graph = entity?.graph;

  useEffect(() => {
    setError(null);
    if (addMode) { setForm(emptyForm()); setSid(schemas[0]?.id ?? ''); return; }
    if (kind === 'schema' && schema) {
      setForm({ name: schema.name, title: schema.title, description: schema.description, status: schema.status, version: schema.version, icon: schema.icon, tags: (schema.tags ?? []).join(', '), oid: '', pid: '', xid: '' });
    } else if (kind === 'config' && config) {
      setForm({ name: config.name, title: config.title, description: config.description, status: config.status, version: config.version, icon: config.icon, tags: (config.tags ?? []).join(', '), oid: config.oid ?? '', pid: config.pid ?? '', xid: config.xid ?? '', sid: config.sid });
    }
  }, [open, addMode, kind, schema, config, schemas]);

  const tagsArr = (s: string) => s.split(',').map((x) => x.trim()).filter(Boolean);

  const handleCreate = async () => {
    setError(null);
    try {
      if (kind === 'schema') {
        if (!form.name.trim()) { setError(t('workflow.nameRequired')); return; }
        await onCreateSchema(form.name.trim(), form.title || undefined, form.description || undefined, form.version || undefined, form.icon, tagsArr(form.tags));
      } else {
        if (sid === '') { setError(t('workflow.sidRequired')); return; }
        await onCreateConfig(Number(sid), form.name.trim() || undefined, form.oid || undefined, form.pid || undefined, form.xid || undefined);
      }
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const handleUpdate = async () => {
    setError(null);
    try {
      const patch: Record<string, unknown> = {
        name: form.name, title: form.title, description: form.description,
        status: form.status, version: form.version, icon: form.icon || undefined, tags: tagsArr(form.tags),
      };
      if (kind === 'config') { patch.oid = form.oid || undefined; patch.pid = form.pid || undefined; patch.xid = form.xid || undefined; }
      await onUpdate(patch);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const handleDelete = async () => {
    setError(null);
    try { await onDelete(); } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const field = (label: string, node: React.ReactNode) => (
    <div className="flex items-center gap-2">
      <label className="w-24 shrink-0 text-xs text-muted-foreground">{label}</label>
      {node}
    </div>
  );

  return (
    <>
      {open && <div className="fixed inset-0 z-40 bg-black/10" onClick={onClose} />}
      <div className={`fixed top-12 right-0 bottom-0 w-[560px] max-w-[92vw] bg-card border-l border-border z-50 flex flex-col
        transition-transform duration-300 ease-in-out pointer-events-none
        ${open ? 'translate-x-0 shadow-2xl pointer-events-auto' : 'translate-x-full shadow-none'}`}>
        <div className="flex items-center justify-between px-5 py-3 border-b border-border bg-muted">
          <h2 className="text-sm text-foreground">
            {addMode ? t('common.add') : t('common.edit')} {t(entityLabelKey(kind))}
          </h2>
          <div className="flex items-center gap-2">
            {!addMode && (
              <button onClick={onEdit}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 transition-colors"
                title={t('workflow.editGraf')}>
                <IconEdit size={13} /> {t('workflow.edit')}
              </button>
            )}
            <button onClick={onClose} className="text-muted-foreground hover:text-foreground p-1 rounded" aria-label={t('common.close')}>
              <IconClose size={18} />
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3">
          {error && <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded px-3 py-2">{error}</div>}

          {/* id is always first */}
          {!addMode && entity && field(t('workflow.fields.id'), <div className={roCls}>{entity.id}</div>)}

          {!addMode && kind === 'config' && (
            <>
              {field('xid', <input className={inputCls} value={form.xid} onChange={(e) => setForm((f) => ({ ...f, xid: e.target.value }))} />)}
              {field(t('workflow.fields.sid'), <div className={`${inputCls} bg-muted`}>{config?.sid}</div>)}
            </>
          )}

          {!addMode && entity && (
            <>              
              {field(t('workflow.fields.ts0'), <div className={roCls}><FormattedTimestamp ts={entity.createdAt} timezone={timezone} /></div>)}
              {field(t('workflow.fields.ts'), <div className={roCls}><FormattedTimestamp ts={entity.updatedAt} timezone={timezone} /></div>)}
            </>
          )}

          {addMode && kind === 'config' && field(t('workflow.fields.schema'),
            <select className={inputCls} value={sid} onChange={(e) => setSid(e.target.value === '' ? '' : Number(e.target.value))}>
              <option value="">{t('workflow.chooseSchema')}</option>
              {schemas.map((s) => <option key={s.id} value={s.id}>#{s.id} {s.name}</option>)}
            </select>
          )}

          {field(t('workflow.fields.name'), <input className={inputCls} value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />)}
          {field(t('workflow.fields.title'), <input className={inputCls} value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} />)}
          {field(t('workflow.fields.desc'), <input className={inputCls} value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} />)}

          {!addMode && (
            <>
              {field(t('workflow.fields.status'),
                <select className={inputCls} value={form.status} onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}>
                  {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
                </select>)}
              {field(t('workflow.fields.version'), <input className={inputCls} value={form.version} onChange={(e) => setForm((f) => ({ ...f, version: e.target.value }))} />)}
              {field(t('workflow.fields.tags'), <TagsInput value={tagsArr(form.tags)} onChange={(arr) => setForm((f) => ({ ...f, tags: arr.join(', ') }))} placeholder={t('workflow.tagsAdd')} />)}
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">{t('workflow.fields.icon')}</label>
                <IconPicker value={form.icon} onChange={(icon) => setForm((f) => ({ ...f, icon }))} />
              </div>
            </>
          )}

          {!addMode && kind === 'config' && (
            <>
              {field('oid', <input className={inputCls} value={form.oid} onChange={(e) => setForm((f) => ({ ...f, oid: e.target.value }))} />)}
              {field('pid', <input className={inputCls} value={form.pid} onChange={(e) => setForm((f) => ({ ...f, pid: e.target.value }))} />)}          
            </>
          )}

          {!addMode && entity && (
            <>
              {field(t('workflow.fields.graph'), <div className={roCls}>
                {t('workflow.graphNodes')}: {graph ? Object.keys(graph.nodes ?? {}).length : 0}, {t('workflow.graphLinks')}: {graph ? Object.keys(graph.links ?? {}).length : 0}
              </div>)}              
            </>
          )}
          
        </div>

        <div className="flex items-center gap-2 px-5 py-2.5 border-t border-border bg-muted">
          {addMode ? (
            <>
              <button onClick={handleCreate} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-40 transition-colors">
                <IconSave size={13} /> {saving ? t('common.creating') : t('common.create')}
              </button>
              <button onClick={onClose} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-card disabled:opacity-40 transition-colors">
                <IconClose size={13} /> {t('common.cancel')}
              </button>
            </>
          ) : (
            <>
              <button onClick={handleUpdate} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-40 transition-colors">
                <IconSave size={13} /> {saving ? t('common.saving') : t('common.update')}
              </button>
              <button onClick={handleDelete} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 disabled:opacity-40 transition-colors">
                <IconTrash size={13} /> {t('common.delete')}
              </button>
              <button onClick={onClose} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-card disabled:opacity-40 transition-colors">
                <IconClose size={13} /> {t('common.cancel')}
              </button>
            </>
          )}
        </div>
      </div>
    </>
  );
}
