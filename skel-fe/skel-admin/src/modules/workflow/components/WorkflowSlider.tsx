import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { WorkflowSchema, WorkflowConfig } from '../types';
import { entityLabelKey, KIND } from '../types';
import type { WorkflowKind } from '../types';
import { IconClose, IconSave, IconTrash, IconEdit, IconRefresh } from '../../../components/Icons';
import { engineIcon } from '../engineIcons';
import { IconPicker } from '../../../components/IconPicker';
import { FormattedTimestamp } from '../../../components/FormattedTimestamp';
import { TagsInput } from '../../../components/TagsInput';
import { SliderFieldRow } from '../../../components/SliderFieldRow';

// lifecycle statuses (WorkflowSchema) + engine runtime statuses (WorkflowConfig, e.g. RUNNING) - see WorkflowStatus.scala
const LIFECYCLE_STATUSES = ['ACTIVE', 'DISABLED', 'DELETED'];
const RUNTIME_STATUSES = ['NEW', 'SCHEDULED', 'STARTING', 'RUNNING', 'RUNNING_FAILED', 'WAITING', 'PAUSED', 'COMPLETED', 'FAILED', 'TERMINATED', 'CANCELED', 'TIMED_OUT', 'CONTINUED_AS_NEW', 'UNRESOLVED', 'UNKNOWN'];

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
  kind: WorkflowKind;
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
  onResolve?: () => Promise<void>; // fetch current engine state via /resolve (config only)
  resolving?: boolean;
}

export function WorkflowSlider(props: WorkflowSliderProps) {
  const { open, addMode, kind, schema, config, schemas, saving, timezone, onClose, onCreateSchema, onCreateConfig, onUpdate, onDelete, onEdit, onResolve, resolving } = props;
  const { t } = useTranslation();
  const [form, setForm] = useState<CommonForm>(emptyForm());
  const [sid, setSid] = useState<number | ''>('');
  const [error, setError] = useState<string | null>(null);

  const entity = kind === KIND.workflowSchema ? schema : config;
  // status options: WorkflowConfig gets the full runtime vocabulary, WorkflowSchema only lifecycle.
  // Always include the current value so an unexpected status still renders as the selected option.
  const statusList = kind === KIND.workflowConfig ? [...LIFECYCLE_STATUSES, ...RUNTIME_STATUSES] : LIFECYCLE_STATUSES;
  const statusOptions = !form.status || statusList.includes(form.status) ? statusList : [form.status, ...statusList];

  useEffect(() => {
    setError(null);
    if (addMode) { setForm(emptyForm()); setSid(schemas[0]?.id ?? ''); return; }
    if (kind === KIND.workflowSchema && schema) {
      setForm({ name: schema.name, title: schema.title, description: schema.description, status: schema.status, version: schema.version, icon: schema.icon, tags: (schema.tags ?? []).join(', '), oid: '', pid: '', xid: '' });
    } else if (kind === KIND.workflowConfig && config) {
      setForm({ name: config.name, title: config.title, description: config.description, status: config.status, version: config.version, icon: config.icon, tags: (config.tags ?? []).join(', '), oid: config.oid ?? '', pid: config.pid ?? '', xid: config.xid ?? '', sid: config.sid });
    }
  }, [open, addMode, kind, schema, config, schemas]);

  const tagsArr = (s: string) => s.split(',').map((x) => x.trim()).filter(Boolean);

  const handleCreate = async () => {
    setError(null);
    try {
      if (kind === KIND.workflowSchema) {
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
      if (kind === KIND.workflowConfig) { patch.oid = form.oid || undefined; patch.pid = form.pid || undefined; patch.xid = form.xid || undefined; }
      await onUpdate(patch);
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const handleDelete = async () => {
    setError(null);
    try { await onDelete(); } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  return (
    <>
      {open && <div className="slider-backdrop" onClick={onClose} />}
      <div className={`slide-panel slide-panel-sm ${open ? 'slide-panel-open' : 'slide-panel-closed'}`}>
        <div className="slide-header">
          <h2 className="slide-title">
            {addMode ? `${t('common.add')} ${t(entityLabelKey(kind))}` : `${t(entityLabelKey(kind))} / ${form.name}`}
          </h2>
          <div className="flex items-center gap-2">
            {!addMode && kind === KIND.workflowConfig && onResolve && (
              <button onClick={() => onResolve()} disabled={resolving} className="btn-design disabled:opacity-40" title={t('workflow.resolve')}>
                <IconRefresh size={13} /> {resolving ? t('workflow.resolving') : t('workflow.resolve')}
              </button>
            )}
            {!addMode && (
              <button onClick={onEdit} className="btn-design" title={t('workflow.editGraf')}>
                <IconEdit size={13} /> {t('workflow.design')}
              </button>
            )}
            <button onClick={onClose} className="slide-close" aria-label={t('common.close')}>
              <IconClose size={18} />
            </button>
          </div>
        </div>

        <div className="slide-body">
          {error && <div className="alert-error">{error}</div>}

          {/* id is always first (runtime status is shown in the `status` field below) */}
          {!addMode && entity && (
            <SliderFieldRow label={t('workflow.fields.id')}>
              <div className="field-readonly">{entity.id}</div>
            </SliderFieldRow>
          )}

          {!addMode && kind === KIND.workflowConfig && (
            <SliderFieldRow label={t('workflow.fields.sid')}>
              <div className="field-readonly">{config?.sid}</div>
            </SliderFieldRow>
          )}

          {!addMode && entity && (
            <>
              <SliderFieldRow label={t('workflow.fields.ts0')}>
                <div className="field-readonly"><FormattedTimestamp ts={entity.createdAt} timezone={timezone} /></div>
              </SliderFieldRow>
              <SliderFieldRow label={t('workflow.fields.ts')}>
                <div className="field-readonly"><FormattedTimestamp ts={entity.updatedAt} timezone={timezone} /></div>
              </SliderFieldRow>
            </>
          )}

          {addMode && kind === KIND.workflowConfig && (
            <SliderFieldRow label={t('workflow.fields.schema')}>
              <select className="field-inline" value={sid} onChange={(e) => setSid(e.target.value === '' ? '' : Number(e.target.value))}>
                <option value="">{t('workflow.chooseSchema')}</option>
                {schemas.map((s) => <option key={s.id} value={s.id}>{s.id} ({s.name})</option>)}
              </select>
            </SliderFieldRow>
          )}

          <SliderFieldRow label={t('workflow.fields.name')}>
            <input className="field-inline" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
          </SliderFieldRow>
          {/* xid: engine runtime id, right after the name; [->] opens the run on the engine panel (meta.uri) */}
          {!addMode && kind === KIND.workflowConfig && (
            <SliderFieldRow label="xid">
              <div className="flex-1 flex items-center gap-1">
                <input className="field-inline flex-1" value={form.xid} onChange={(e) => setForm((f) => ({ ...f, xid: e.target.value }))} />
                {config?.meta?.uri ? (() => {
                  const EngineIcon = engineIcon(config.meta.engine ? String(config.meta.engine) : undefined);
                  return (
                    <button
                      type="button"
                      className="p-1 rounded shrink-0 border border-border text-muted-foreground hover:text-foreground hover:bg-muted transition-colors"
                      title={t('workflow.openEngine', { uri: String(config.meta!.uri) })}
                      onClick={() => window.open(String(config.meta!.uri), '_blank', 'noopener,noreferrer')}
                    >
                      <EngineIcon size={14} />
                    </button>
                  );
                })() : null}
              </div>
            </SliderFieldRow>
          )}
          <SliderFieldRow label={t('workflow.fields.title')}>
            <input className="field-inline" value={form.title} onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))} />
          </SliderFieldRow>
          <SliderFieldRow label={t('workflow.fields.desc')}>
            <input className="field-inline" value={form.description} onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))} />
          </SliderFieldRow>

          {!addMode && (
            <>
              <SliderFieldRow label={t('workflow.fields.status')}>
                <select className="field-inline" value={form.status} onChange={(e) => setForm((f) => ({ ...f, status: e.target.value }))}>
                  {statusOptions.map((s) => <option key={s} value={s}>{s}</option>)}
                </select>
              </SliderFieldRow>
              <SliderFieldRow label={t('workflow.fields.version')}>
                <input className="field-inline" value={form.version} onChange={(e) => setForm((f) => ({ ...f, version: e.target.value }))} />
              </SliderFieldRow>
              <SliderFieldRow label={t('workflow.fields.tags')}>
                <TagsInput value={tagsArr(form.tags)} onChange={(arr) => setForm((f) => ({ ...f, tags: arr.join(', ') }))} placeholder={t('workflow.tagsAdd')} />
              </SliderFieldRow>
              <div className="field-stack">
                <label className="field-stack-label">{t('workflow.fields.icon')}</label>
                <IconPicker value={form.icon} onChange={(icon) => setForm((f) => ({ ...f, icon }))} w={12} />
              </div>
            </>
          )}

          {!addMode && kind === KIND.workflowConfig && (
            <>
              <SliderFieldRow label="oid">
                <input className="field-inline" value={form.oid} onChange={(e) => setForm((f) => ({ ...f, oid: e.target.value }))} />
              </SliderFieldRow>
              <SliderFieldRow label="pid">
                <input className="field-inline" value={form.pid} onChange={(e) => setForm((f) => ({ ...f, pid: e.target.value }))} />
              </SliderFieldRow>
              {/* engine-derived metadata (wid / err / activity_id ...) - read-only */}
              {config?.meta && Object.keys(config.meta).length > 0 && (
                <div className="field-stack">
                  <label className="field-stack-label">{t('workflow.fields.meta')}</label>
                  <pre className="code-block-sm">{JSON.stringify(config.meta, null, 2)}</pre>
                </div>
              )}
            </>
          )}

        </div>

        <div className="slide-footer">
          {addMode ? (
            <>
              <button onClick={handleCreate} disabled={saving} className="btn-add">
                <IconSave size={13} /> {saving ? t('common.creating') : t('common.create')}
              </button>
              <button onClick={onClose} disabled={saving} className="btn-cancel">
                <IconClose size={13} /> {t('common.cancel')}
              </button>
            </>
          ) : (
            <>
              <button onClick={handleUpdate} disabled={saving} className="btn-add">
                <IconSave size={13} /> {saving ? t('common.saving') : t('common.update')}
              </button>
              <button onClick={handleDelete} disabled={saving} className="btn-danger">
                <IconTrash size={13} /> {t('common.delete')}
              </button>
              <button onClick={onClose} disabled={saving} className="btn-cancel">
                <IconClose size={13} /> {t('common.cancel')}
              </button>
            </>
          )}
        </div>
      </div>
    </>
  );
}
