import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { DetectorSchema, DetectorConfig } from '../types';
import { entityLabelKey, KIND } from '../types';
import type { DetectorKind } from '../types';
import { IconClose, IconSave, IconTrash } from '../../../components/Icons';
import { IconPicker } from '../../../components/IconPicker';
import { FormattedTimestamp } from '../../../components/FormattedTimestamp';
import { TagsInput } from '../../../components/TagsInput';
import { SliderFieldRow } from '../../../components/SliderFieldRow';

// lifecycle statuses (DetectorSchema) + engine runtime statuses (DetectorConfig, e.g. RUNNING) - see WorkflowStatus.scala
const LIFECYCLE_STATUSES = ['ACTIVE', 'DISABLED', 'DELETED'];
const RUNTIME_STATUSES = ['NEW', 'SCHEDULED', 'STARTING', 'RUNNING', 'RUNNING_FAILED', 'WAITING', 'PAUSED', 'COMPLETED', 'FAILED', 'TERMINATED', 'CANCELED', 'TIMED_OUT', 'CONTINUED_AS_NEW', 'UNRESOLVED', 'UNKNOWN'];

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
  extended?: boolean;              // "Detector" tab: read-only enriched view - adds Contract + Schema sections
  onClose: () => void;
  onCreateSchema: (req: { name: string; title?: string; description?: string; version?: string; author?: string; icon?: string; tags?: string[]; schema?: Record<string, unknown>; uiSchema?: Record<string, unknown> }) => Promise<void>;
  onCreateConfig: (req: { name: string; sid?: number; source?: string; tags?: string[]; config?: Record<string, unknown> }) => Promise<void>;
  onUpdateSchema: (patch: { name?: string; title?: string; description?: string; version?: string; author?: string; status?: string; icon?: string; tags?: string[]; schema?: Record<string, unknown>; uiSchema?: Record<string, unknown> }) => Promise<void>;
  onUpdateConfig: (patch: { name?: string; status?: string; source?: string; tags?: string[]; config?: Record<string, unknown> }) => Promise<void>;
  onOpenSchema?: (id: number) => void; // open the DetectorSchema referenced by a DetectorConfig
  onDelete: () => Promise<void>;
}

export function DetectorSlider(props: DetectorSliderProps) {
  const { open, addMode, kind, schema, config, schemas, saving, timezone, readOnly, extended, onClose, onCreateSchema, onCreateConfig, onUpdateSchema, onUpdateConfig, onOpenSchema, onDelete } = props;
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
  const [schemaJson, setSchemaJson] = useState('');     // DetectorSchema.schema (JSON)
  const [uiSchemaJson, setUiSchemaJson] = useState(''); // DetectorSchema.uiSchema (JSON)

  useEffect(() => {
    setError(null);
    if (addMode) {
      setName(''); setTitle(''); setDescription(''); setVersion('1.0.0'); setAuthor(''); setIcon(undefined);
      setStatus('ACTIVE'); setSource(''); setTags(''); setSid(schemas[0]?.id ?? ''); setConfigJson('');
      setSchemaJson(''); setUiSchemaJson('');
      return;
    }
    if (kind === KIND.detectorSchema && schema) {
      setName(schema.name); setTitle(schema.title); setDescription(schema.description); setVersion(schema.version);
      setAuthor(schema.author); setIcon(schema.icon); setStatus(schema.status); setTags((schema.tags ?? []).join(', '));
      setSchemaJson(schema.schema ? JSON.stringify(schema.schema, null, 2) : '');
      setUiSchemaJson(schema.uiSchema ? JSON.stringify(schema.uiSchema, null, 2) : '');
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
  const parseJsonObj = (s: string): Record<string, unknown> | undefined => {
    if (!s.trim()) return undefined;
    return JSON.parse(s) as Record<string, unknown>;
  };

  const handleCreate = async () => {
    setError(null);
    try {
      if (!name.trim()) { setError(t('workflow.nameRequired')); return; }
      if (kind === KIND.detectorSchema) {
        let sch: Record<string, unknown> | undefined;
        let uiSch: Record<string, unknown> | undefined;
        try { sch = parseJsonObj(schemaJson); uiSch = parseJsonObj(uiSchemaJson); } catch { setError(t('workflow.invalidJson')); return; }
        await onCreateSchema({ name: name.trim(), title: title || undefined, description: description || undefined, version: version || undefined, author: author || undefined, icon, tags: tagsArr(tags), schema: sch, uiSchema: uiSch });
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
        let sch: Record<string, unknown> | undefined;
        let uiSch: Record<string, unknown> | undefined;
        try { sch = parseJsonObj(schemaJson); uiSch = parseJsonObj(uiSchemaJson); } catch { setError(t('workflow.invalidJson')); return; }
        await onUpdateSchema({ name, title, description, version, author, status, icon, tags: tagsArr(tags), schema: sch, uiSchema: uiSch });
      } else {
        // DetectorConfig: status IS editable (editable combo)
        let cfg: Record<string, unknown> | undefined;
        try { cfg = parseConfig(); } catch { setError(t('workflow.invalidJson')); return; }
        await onUpdateConfig({ name, status, source, tags: tagsArr(tags), config: cfg });
      }
    } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const handleDelete = async () => {
    setError(null);
    try { await onDelete(); } catch (e) { setError(e instanceof Error ? e.message : String(e)); }
  };

  const isSchema = kind === KIND.detectorSchema;
  // read-only when opened from the editor OR in the "Detector" extended (enriched, view-only) mode
  const viewOnly = !!readOnly || !!extended;
  const kindLabel = extended ? t(entityLabelKey(KIND.detector)) : t(entityLabelKey(kind));
  // status options: DetectorConfig gets the full runtime vocabulary, DetectorSchema only lifecycle.
  // Always include the current value so an unexpected status still renders as the selected option.
  const statusList = isSchema ? LIFECYCLE_STATUSES : [...LIFECYCLE_STATUSES, ...RUNTIME_STATUSES];
  const statusOptions = !status || statusList.includes(status) ? statusList : [status, ...statusList];

  return (
    <>
      {open && <div className="slider-backdrop" onClick={onClose} />}
      <div className={`slide-panel slide-panel-sm ${open ? 'slide-panel-open' : 'slide-panel-closed'}`}>
        <div className="slide-header">
          {/* "{entity} / {name}" so it is clear which object is being edited (Add has no name yet) */}
          <h2 className="slide-title">
            {addMode ? `${t('common.add')} ${kindLabel}` : `${kindLabel} / ${name}`}
          </h2>
          <button onClick={onClose} className="slide-close" aria-label={t('common.close')}>
            <IconClose size={18} />
          </button>
        </div>

        <div className="slide-body">
          {error && <div className="alert-error">{error}</div>}

          {!addMode && (
            <SliderFieldRow label={t('workflow.fields.id')}>
              <div className="field-readonly">{(isSchema ? schema?.id : config?.id) ?? ''}</div>
            </SliderFieldRow>
          )}
          {!addMode && (
            <SliderFieldRow label={t('workflow.fields.ts0')}>
              <div className="field-readonly"><FormattedTimestamp ts={(isSchema ? schema?.createdAt : config?.createdAt) ?? 0} timezone={timezone} /></div>
            </SliderFieldRow>
          )}
          {!addMode && (
            <SliderFieldRow label={t('workflow.fields.ts')}>
              <div className="field-readonly"><FormattedTimestamp ts={(isSchema ? schema?.updatedAt : config?.updatedAt) ?? 0} timezone={timezone} /></div>
            </SliderFieldRow>
          )}

          <SliderFieldRow label={t('workflow.fields.name')}>
            {viewOnly ? <div className="field-readonly">{name}</div>
              : <input className="field-inline" value={name} onChange={(e) => setName(e.target.value)} />}
          </SliderFieldRow>

          <SliderFieldRow label={t('workflow.fields.status')}>
            {viewOnly
              ? <div className="field-readonly">{status}</div>
              : <select className="field-inline" value={status} onChange={(e) => setStatus(e.target.value)}>
                  {statusOptions.map((s) => <option key={s} value={s}>{s}</option>)}
                </select>}
          </SliderFieldRow>

          <SliderFieldRow label={t('workflow.fields.tags')}>
            <TagsInput value={tagsArr(tags)} onChange={(arr) => setTags(arr.join(', '))} readOnly={viewOnly} placeholder={t('workflow.tagsAdd')} />
          </SliderFieldRow>      

          {isSchema && (
            <>
              <SliderFieldRow label={t('workflow.fields.title')}>
                {viewOnly ? <div className="field-readonly">{title || ''}</div>
                  : <input className="field-inline" value={title} onChange={(e) => setTitle(e.target.value)} />}
              </SliderFieldRow>
              
              <SliderFieldRow label={t('workflow.fields.desc')}>
                {viewOnly ? <div className="field-readonly">{description || ''}</div>
                  : <input className="field-inline" value={description} onChange={(e) => setDescription(e.target.value)} />}
              </SliderFieldRow>
              <SliderFieldRow label={t('workflow.fields.version')}>
                {viewOnly ? <div className="field-readonly">{version}</div>
                  : <input className="field-inline" value={version} onChange={(e) => setVersion(e.target.value)} />}
              </SliderFieldRow>
              <SliderFieldRow label={t('workflow.fields.author')}>
                {viewOnly ? <div className="field-readonly">{author || ''}</div>
                  : <input className="field-inline" value={author} onChange={(e) => setAuthor(e.target.value)} />}
              </SliderFieldRow>
              {!viewOnly && (
                <div className="field-stack">
                  <label className="field-stack-label">{t('workflow.fields.icon')}</label>
                  <IconPicker value={icon} onChange={setIcon} w={12} />
                </div>
              )}
              {viewOnly && (
                <SliderFieldRow label={t('workflow.fields.networkTags')}>
                  <div className="field-readonly">{(schema?.networkTags ?? []).join(', ') || ''}</div>
                </SliderFieldRow>
              )}
              <div className="field-stack">
                <label className="field-stack-label">schema</label>
                {viewOnly
                  ? <pre className="code-block-sm">{schemaJson || ''}</pre>
                  : <textarea rows={8} spellCheck={false} value={schemaJson} onChange={(e) => setSchemaJson(e.target.value)}
                      placeholder={'{\n  "type": "object",\n  "properties": {}\n}'} className="field-code" />}
              </div>
              <div className="field-stack">
                <label className="field-stack-label">uiSchema</label>
                {viewOnly
                  ? <pre className="code-block-sm">{uiSchemaJson || ''}</pre>
                  : <textarea rows={4} spellCheck={false} value={uiSchemaJson} onChange={(e) => setUiSchemaJson(e.target.value)}
                      placeholder={'{\n  "ui:order": []\n}'} className="field-code" />}
              </div>
            </>
          )}
          
          {!isSchema && (
            <>
              {addMode && (
                <SliderFieldRow label={t('workflow.fields.schema')}>
                  <select className="field-inline" value={sid} onChange={(e) => setSid(e.target.value === '' ? '' : Number(e.target.value))}>
                    <option value="">{t('workflow.chooseSchema')}</option>
                    {schemas.map((s) => <option key={s.id} value={s.id}>{s.id} ({s.name})</option>)}
                  </select>
                </SliderFieldRow>
              )}
              {!addMode && (
                <SliderFieldRow label={t('workflow.fields.schema')}>
                  <div className="field-readonly flex-1">{config?.schema ? `${config.schema.id} (${config.schema.name})` : ''}</div>
                  {config?.schema && onOpenSchema && (
                    <button type="button" onClick={() => onOpenSchema(config.schema!.id)} title={t('workflow.tabs.detectorSchema')}
                      className="shrink-0 inline-flex items-center justify-center w-[26px] h-[26px] rounded border border-border text-muted-foreground hover:bg-muted transition-colors text-base leading-none">
                      …
                    </button>
                  )}
                </SliderFieldRow>
              )}
              <SliderFieldRow label={t('workflow.fields.source')}>
                {viewOnly ? <div className="field-readonly">{source || ''}</div>
                  : <input className="field-inline" value={source} onChange={(e) => setSource(e.target.value)} />}
              </SliderFieldRow>
              <div className="field-stack">
                <label className="field-stack-label">config (JSON)</label>
                {viewOnly
                  ? <pre className="code-block-sm">{configJson || ''}</pre>
                  : <textarea rows={14} spellCheck={false} value={configJson} onChange={(e) => setConfigJson(e.target.value)}
                      placeholder={'{\n  "severity": 0.5\n}'}
                      className="field-code" />}
              </div>

              {/* "Detector" extended view: enriched read-only info from the associated Contract + DetectorSchema */}
              {extended && !addMode && (
                <>
                  <div className="pt-3 pb-1 text-xs font-semibold uppercase text-muted-foreground">{t('workflow.fields.contract')}</div>
                  <SliderFieldRow label={t('workflow.fields.id')}>
                    <div className="field-readonly">{config?.contract?.id ?? ''}</div>
                  </SliderFieldRow>
                  <SliderFieldRow label={t('workflow.fields.tenant')}>
                    <div className="field-readonly">{config?.contract?.tenantId ?? ''}</div>
                  </SliderFieldRow>
                  <SliderFieldRow label={t('workflow.fields.project')}>
                    <div className="field-readonly">{config?.contract?.projectId ?? ''}</div>
                  </SliderFieldRow>
                  <SliderFieldRow label={t('workflow.fields.name')}>
                    <div className="field-readonly">{config?.contract?.name ?? ''}</div>
                  </SliderFieldRow>
                  <SliderFieldRow label={t('workflow.fields.address')}>
                    <div className="field-readonly">{config?.contract?.address ?? ''}</div>
                  </SliderFieldRow>
                  <SliderFieldRow label={t('workflow.fields.chainUid')}>
                    <div className="field-readonly">{config?.contract?.chainUid ?? ''}</div>
                  </SliderFieldRow>

                  <div className="pt-3 pb-1 text-xs font-semibold uppercase text-muted-foreground">{t('workflow.fields.schema')}</div>
                  <SliderFieldRow label={t('workflow.fields.id')}>
                    <div className="field-readonly">{config?.schema?.id ?? ''}</div>
                  </SliderFieldRow>
                  <SliderFieldRow label={t('workflow.fields.name')}>
                    <div className="field-readonly">{config?.schema?.name ?? ''}</div>
                  </SliderFieldRow>
                  <SliderFieldRow label={t('workflow.fields.version')}>
                    <div className="field-readonly">{config?.schema?.version ?? ''}</div>
                  </SliderFieldRow>
                  <SliderFieldRow label={t('workflow.fields.status')}>
                    <div className="field-readonly">{config?.schema?.status ?? ''}</div>
                  </SliderFieldRow>
                </>
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
              {!viewOnly && (
                <button onClick={handleUpdate} disabled={saving} className="btn-add">
                  <IconSave size={13} /> {saving ? t('common.saving') : t('common.update')}
                </button>
              )}
              {!readOnly && (
                <button onClick={handleDelete} disabled={saving} className="btn-danger">
                  <IconTrash size={13} /> {t('common.delete')}
                </button>
              )}
              <button onClick={onClose} disabled={saving} className="btn-cancel">
                <IconClose size={13} /> {t('common.close')}
              </button>
            </>
          )}
        </div>
      </div>
    </>
  );
}
