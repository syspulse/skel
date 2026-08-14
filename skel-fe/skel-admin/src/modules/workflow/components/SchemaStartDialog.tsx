import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { IconClose, IconPlay } from '../../../components/Icons';
import { SliderFieldRow } from '../../../components/SliderFieldRow';
import { SchemaConfigEditor, defaultConfig } from './SchemaConfigEditor';
import type { JsonSchema, UiSchema } from './SchemaConfigEditor';

interface SchemaStartDialogProps {
  open: boolean;
  schemaId?: number;     // shown in the title (the schema being started)
  schemaName?: string;   // shown in the title
  schema?: JsonSchema;   // WorkflowSchema.schema — drives the input form
  uiSchema?: UiSchema;   // WorkflowSchema.uiSchema — ui:order / widgets
  defaultTaskQueue?: string; // pre-fill from WorkflowSchema.meta.tq
  defaultNs?: string;        // pre-fill from WorkflowSchema.meta.ns
  defaultOid?: string;       // pre-fill owner id from the user profile (see useDefaultOid)
  saving: boolean;
  onClose: () => void;
  // input: parsed JSON (undefined when empty -> server uses the default WorkflowConfig payload)
  onStart: (input: unknown | undefined, taskQueue?: string, wid?: string, ns?: string, oid?: string, pid?: string) => void;
}

/** Modal to start a workflow from a WorkflowSchema: schema-driven input + optional tq / ns / oid / wid. */
export function SchemaStartDialog(props: SchemaStartDialogProps) {
  const { open, schemaId, schemaName, schema, uiSchema, defaultTaskQueue, defaultNs, defaultOid, saving, onClose, onStart } = props;
  const { t } = useTranslation();
  const [input, setInput] = useState<unknown>({});
  const [taskQueue, setTaskQueue] = useState('');
  const [ns, setNs] = useState('');
  const [oid, setOid] = useState('');
  const [pid, setPid] = useState('');
  const [wid, setWid] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setInput(defaultConfig(schema));
      setTaskQueue(defaultTaskQueue ?? '');
      setNs(defaultNs ?? '');
      setOid(defaultOid ?? '');
      setPid('');
      setWid('');
      setError(null);
    }
  }, [open, defaultTaskQueue, defaultNs, defaultOid, schema]);

  if (!open) return null;

  const handleStart = () => {
    setError(null);
    const empty = input == null || (typeof input === 'object' && !Array.isArray(input) && Object.keys(input as object).length === 0);
    onStart(empty && !schema ? undefined : input, taskQueue.trim() || undefined, wid.trim() || undefined, ns.trim() || undefined, oid.trim() || undefined, pid.trim() || undefined);
  };

  return (
    <>
      <div className="slider-backdrop" onClick={onClose} />
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none">
        <div className="pointer-events-auto w-full max-w-3xl max-h-[90vh] bg-card border border-border rounded shadow-lg flex flex-col">
          <div className="slide-header">
            <h2 className="slide-title">
              WorkflowSchema{schemaId != null ? ` / ${schemaId}` : ''}{schemaName ? ` / ${schemaName}` : ''}
            </h2>
            <button onClick={onClose} className="slide-close" aria-label={t('common.close')}>
              <IconClose size={18} />
            </button>
          </div>

          <div className="slide-body">
            {error && <div className="alert-error">{error}</div>}

            <div className="field-stack">
              <label className="field-stack-label">{t('workflow.fields.inputJson')}</label>
              <SchemaConfigEditor
                schema={schema}
                uiSchema={uiSchema}
                value={input}
                onChange={setInput}
                height={320}
              />
            </div>

            <SliderFieldRow label={t('workflow.fields.taskQueue')}>
              <input className="field-inline" value={taskQueue} onChange={(e) => setTaskQueue(e.target.value)} />
            </SliderFieldRow>
            <SliderFieldRow label="namespace">
              <input className="field-inline" value={ns} onChange={(e) => setNs(e.target.value)} />
            </SliderFieldRow>
            <SliderFieldRow label="oid">
              <input className="field-inline" value={oid} onChange={(e) => setOid(e.target.value)} />
            </SliderFieldRow>
            <SliderFieldRow label="pid">
              <input className="field-inline" value={pid} onChange={(e) => setPid(e.target.value)} />
            </SliderFieldRow>
            <SliderFieldRow label={t('workflow.fields.workflowId')}>
              <input className="field-inline" value={wid} onChange={(e) => setWid(e.target.value)} />
            </SliderFieldRow>
          </div>

          <div className="slide-footer">
            <button onClick={handleStart} disabled={saving} className="btn-add">
              <IconPlay size={13} /> {saving ? t('workflow.starting') : t('workflow.start')}
            </button>
            <button onClick={onClose} disabled={saving} className="btn-cancel">
              <IconClose size={13} /> {t('common.cancel')}
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
