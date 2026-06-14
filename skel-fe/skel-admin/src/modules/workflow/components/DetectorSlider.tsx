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

const STATUSES = ['ACTIVE', 'DISABLED', 'DELETED'];

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
      {open && <div className="slider-backdrop" onClick={onClose} />}
      <div className={`slide-panel slide-panel-sm ${open ? 'slide-panel-open' : 'slide-panel-closed'}`}>
        <div className="slide-header">
          <h2 className="slide-title">
            {addMode ? t('common.add') : (viewOnly ? t('common.view') : t('common.edit'))} {kindLabel}
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
                  <IconPicker value={icon} onChange={setIcon} />
                </div>
              )}
              {viewOnly && (
                <SliderFieldRow label={t('workflow.fields.networkTags')}>
                  <div className="field-readonly">{(schema?.networkTags ?? []).join(', ') || ''}</div>
                </SliderFieldRow>
              )}
              {viewOnly && schema?.schema && (
                <div className="field-stack">
                  <label className="field-stack-label">schema</label>
                  <pre className="code-block-sm">{JSON.stringify(schema.schema, null, 2)}</pre>
                </div>
              )}
            </>
          )}

          <SliderFieldRow label={t('workflow.fields.status')}>
            {(isSchema && !viewOnly)
              ? <select className="field-inline" value={status} onChange={(e) => setStatus(e.target.value)}>{STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}</select>
              : <div className="field-readonly">{status}</div>}
          </SliderFieldRow>

          <SliderFieldRow label={t('workflow.fields.tags')}>
            <TagsInput value={tagsArr(tags)} onChange={(arr) => setTags(arr.join(', '))} readOnly={viewOnly} placeholder={t('workflow.tagsAdd')} />
          </SliderFieldRow>

          {!isSchema && (
            <>
              {addMode && (
                <SliderFieldRow label={t('workflow.fields.schema')}>
                  <select className="field-inline" value={sid} onChange={(e) => setSid(e.target.value === '' ? '' : Number(e.target.value))}>
                    <option value="">{t('workflow.chooseSchema')}</option>
                    {schemas.map((s) => <option key={s.id} value={s.id}>#{s.id} {s.name}</option>)}
                  </select>
                </SliderFieldRow>
              )}
              {!addMode && (
                <SliderFieldRow label={t('workflow.fields.schema')}>
                  <div className="field-readonly">{config?.schema ? `#${config.schema.id} ${config.schema.name}` : ''}</div>
                </SliderFieldRow>
              )}
              <SliderFieldRow label={t('workflow.fields.source')}>
                <input className="field-inline" value={source} onChange={(e) => setSource(e.target.value)} />
              </SliderFieldRow>
              <div className="field-stack">
                <label className="field-stack-label">config (JSON)</label>
                <textarea rows={6} spellCheck={false} value={configJson} onChange={(e) => setConfigJson(e.target.value)}
                  placeholder={'{\n  "severity": 0.5\n}'}
                  className="field-code" />
              </div>
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
