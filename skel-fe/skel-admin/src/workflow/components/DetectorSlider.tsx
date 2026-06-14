import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { DetectorSchema, DetectorConfig } from '../types';
import { entityLabelKey, KIND } from '../types';
import type { DetectorKind } from '../types';
import { IconClose, IconSave, IconTrash } from '../../components/Icons';
import { IconPicker } from '../editor/IconPicker';
import { FormattedTimestamp } from '../../components/FormattedTimestamp';
import { TagsInput } from '../../components/TagsInput';

const STATUSES = ['ACTIVE', 'DISABLED', 'DELETED'];
const inputCls = 'flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400';
const roCls = 'flex-1 text-sm bg-muted border border-border rounded px-3 py-1 text-foreground select-text';

interface DetectorSliderProps {
  open: boolean;
  addMode: boolean;
  kind: DetectorKind;
  schema: DetectorSchema | null;
  config: DetectorConfig | null;
  schemas: DetectorSchema[];       // for config create (choose source DetectorSchema)
  saving: boolean;
  timezone: string;
  readOnly?: boolean;              // when opened from the editor: view only, no edit/delete
  onClose: () => void;
  onCreateSchema: (req: { name: string; title?: string; description?: string; version?: string; author?: string; icon?: string; tags?: string[] }) => Promise<void>;
  onCreateConfig: (req: { name: string; sid?: number; source?: string; tags?: string[]; config?: Record<string, unknown> }) => Promise<void>;
  onUpdateSchema: (patch: { name?: string; title?: string; description?: string; version?: string; author?: string; status?: string; icon?: string; tags?: string[] }) => Promise<void>;
  onUpdateConfig: (patch: { name?: string; source?: string; tags?: string[]; config?: Record<string, unknown> }) => Promise<void>;
  onDelete: () => Promise<void>;
}

function field(label: string, node: React.ReactNode) {
  return (
    <div className="flex items-center gap-2">
      <label className="w-24 shrink-0 text-xs text-muted-foreground">{label}</label>
      {node}
    </div>
  );
}

export function DetectorSlider(props: DetectorSliderProps) {
  const { open, addMode, kind, schema, config, schemas, saving, timezone, readOnly, onClose, onCreateSchema, onCreateConfig, onUpdateSchema, onUpdateConfig, onDelete } = props;
  const { t } = useTranslation();
  const [error, setError] = useState<string | null>(null);

  // form state (covers both kinds; only relevant fields used)
  const [name, setName] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [version, setVersion] = useState('1.0.0');
  const [author, setAuthor] = useState('');
  const [icon, setIcon] = useState<string | undefined>(undefined);
  const [status, setStatus] = useState('ACTIVE');
  const [source, setSource] = useState('');
  const [tags, setTags] = useState('');
  const [sid, setSid] = useState<number | ''>('');
  const [configJson, setConfigJson] = useState('');

  useEffect(() => {
    setError(null);
    if (addMode) {
      setName(''); setTitle(''); setDescription(''); setVersion('1.0.0'); setAuthor(''); setIcon(undefined);
      setStatus('ACTIVE'); setSource(''); setTags(''); setSid(schemas[0]?.id ?? ''); setConfigJson('');
      return;
    }
    if (kind === KIND.detectorSchema && schema) {
      setName(schema.name); setTitle(schema.title); setDescription(schema.description); setVersion(schema.version);
      setAuthor(schema.author); setIcon(schema.icon); setStatus(schema.status); setTags((schema.tags ?? []).join(', '));
    } else if (kind === KIND.detectorConfig && config) {
      setName(config.name); setStatus(config.status); setSource(config.source); setTags((config.tags ?? []).join(', '));
      setConfigJson(config.config ? JSON.stringify(config.config, null, 2) : '');
    }
  }, [open, addMode, kind, schema, config, schemas]);

  const tagsArr = (s: string) => s.split(',').map((x) => x.trim()).filter(Boolean);
  const parseConfig = (): Record<string, unknown> | undefined => {
    if (!configJson.trim()) return undefined;
    return JSON.parse(configJson) as Record<string, unknown>;
  };

  const handleCreate = async () => {
    setError(null);
    try {
      if (!name.trim()) { setError(t('workflow.nameRequired')); return; }
      if (kind === KIND.detectorSchema) {
        await onCreateSchema({ name: name.trim(), title: title || undefined, description: description || undefined, version: version || undefined, author: author || undefined, icon, tags: tagsArr(tags) });
      } else {
        let cfg: Record<string, unknown> | undefined;
        try { cfg = parseConfig(); } catch { setError(t('workflow.invalidJson')); return; }
        await onCreateConfig({ name: name.trim(), sid: sid === '' ? undefined : Number(sid), source: source || undefined, tags: tagsArr(tags), config: cfg });
      }
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const handleUpdate = async () => {
    setError(null);
    try {
      if (kind === KIND.detectorSchema) {
        // status IS editable for DetectorSchema
        await onUpdateSchema({ name, title, description, version, author, status, icon, tags: tagsArr(tags) });
      } else {
        // DetectorConfig: status is NOT editable -> not sent
        let cfg: Record<string, unknown> | undefined;
        try { cfg = parseConfig(); } catch { setError(t('workflow.invalidJson')); return; }
        await onUpdateConfig({ name, source, tags: tagsArr(tags), config: cfg });
      }
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const handleDelete = async () => {
    setError(null);
    try { await onDelete(); } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const isSchema = kind === KIND.detectorSchema;
  const viewOnly = !!readOnly; // both DetectorSchema and DetectorConfig are editable
  const kindLabel = t(entityLabelKey(kind));

  return (
    <>
      {open && <div className="fixed inset-0 z-40 bg-black/10" onClick={onClose} />}
      <div className={`fixed top-12 right-0 bottom-0 w-[560px] max-w-[92vw] bg-card border-l border-border z-50 flex flex-col
        transition-transform duration-300 ease-in-out pointer-events-none
        ${open ? 'translate-x-0 shadow-2xl pointer-events-auto' : 'translate-x-full shadow-none'}`}>
        <div className="flex items-center justify-between px-5 py-3 border-b border-border bg-muted">
          <h2 className="text-sm text-foreground">
            {addMode ? t('common.add') : (viewOnly ? t('common.view') : t('common.edit'))} {kindLabel}
          </h2>
          <button onClick={onClose} className="text-muted-foreground hover:text-foreground p-1 rounded" aria-label={t('common.close')}>
            <IconClose size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3">
          {error && <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded px-3 py-2">{error}</div>}

          {!addMode && field(t('workflow.fields.id'), <div className={roCls}>{(isSchema ? schema?.id : config?.id) ?? ''}</div>)}
          {!addMode && field(t('workflow.fields.ts0'), <div className={roCls}><FormattedTimestamp ts={(isSchema ? schema?.createdAt : config?.createdAt) ?? 0} timezone={timezone} /></div>)}
          {!addMode && field(t('workflow.fields.ts'), <div className={roCls}><FormattedTimestamp ts={(isSchema ? schema?.updatedAt : config?.updatedAt) ?? 0} timezone={timezone} /></div>)}

          {field(t('workflow.fields.name'),
            viewOnly ? <div className={roCls}>{name}</div>
              : <input className={inputCls} value={name} onChange={(e) => setName(e.target.value)} />)}

          {isSchema && (
            <>
              {field(t('workflow.fields.title'),
                viewOnly ? <div className={roCls}>{title || ''}</div>
                  : <input className={inputCls} value={title} onChange={(e) => setTitle(e.target.value)} />)}
              {field(t('workflow.fields.desc'),
                viewOnly ? <div className={roCls}>{description || ''}</div>
                  : <input className={inputCls} value={description} onChange={(e) => setDescription(e.target.value)} />)}
              {field(t('workflow.fields.version'),
                viewOnly ? <div className={roCls}>{version}</div>
                  : <input className={inputCls} value={version} onChange={(e) => setVersion(e.target.value)} />)}
              {field(t('workflow.fields.author'),
                viewOnly ? <div className={roCls}>{author || ''}</div>
                  : <input className={inputCls} value={author} onChange={(e) => setAuthor(e.target.value)} />)}
              {!viewOnly && (
                <div className="space-y-1">
                  <label className="text-xs text-muted-foreground">{t('workflow.fields.icon')}</label>
                  <IconPicker value={icon} onChange={setIcon} />
                </div>
              )}
              {viewOnly && field(t('workflow.fields.networkTags'), <div className={roCls}>{(schema?.networkTags ?? []).join(', ') || ''}</div>)}
              {viewOnly && schema?.schema && (
                <div className="space-y-1">
                  <label className="text-xs text-muted-foreground">schema</label>
                  <pre className="text-[11px] font-mono bg-muted border border-border rounded px-2 py-1.5 overflow-auto max-h-48">{JSON.stringify(schema.schema, null, 2)}</pre>
                </div>
              )}
            </>
          )}

          {/* status is editable ONLY for DetectorSchema; read-only for DetectorConfig */}
          {field(t('workflow.fields.status'),
            (isSchema && !viewOnly)
              ? <select className={inputCls} value={status} onChange={(e) => setStatus(e.target.value)}>{STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}</select>
              : <div className={roCls}>{status}</div>)}

          {field(t('workflow.fields.tags'),
            <TagsInput value={tagsArr(tags)} onChange={(arr) => setTags(arr.join(', '))} readOnly={viewOnly} placeholder={t('workflow.tagsAdd')} />)}

          {!isSchema && (
            <>
              {addMode && field(t('workflow.fields.schema'),
                <select className={inputCls} value={sid} onChange={(e) => setSid(e.target.value === '' ? '' : Number(e.target.value))}>
                  <option value="">{t('workflow.chooseSchema')}</option>
                  {schemas.map((s) => <option key={s.id} value={s.id}>#{s.id} {s.name}</option>)}
                </select>)}
              {!addMode && field(t('workflow.fields.schema'), <div className={roCls}>{config?.schema ? `#${config.schema.id} ${config.schema.name}` : ''}</div>)}
              {field(t('workflow.fields.source'), <input className={inputCls} value={source} onChange={(e) => setSource(e.target.value)} />)}
              <div className="space-y-1">
                <label className="text-xs text-muted-foreground">config (JSON)</label>
                <textarea rows={6} spellCheck={false} value={configJson} onChange={(e) => setConfigJson(e.target.value)}
                  placeholder={'{\n  "severity": 0.5\n}'}
                  className="w-full text-xs font-mono border border-input rounded px-2 py-1.5 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 resize-y" />
              </div>
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
              {!viewOnly && (
                <button onClick={handleUpdate} disabled={saving}
                  className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-40 transition-colors">
                  <IconSave size={13} /> {saving ? t('common.saving') : t('common.update')}
                </button>
              )}
              {!readOnly && (
                <button onClick={handleDelete} disabled={saving}
                  className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 disabled:opacity-40 transition-colors">
                  <IconTrash size={13} /> {t('common.delete')}
                </button>
              )}
              <button onClick={onClose} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-card disabled:opacity-40 transition-colors">
                <IconClose size={13} /> {t('common.close')}
              </button>
            </>
          )}
        </div>
      </div>
    </>
  );
}
