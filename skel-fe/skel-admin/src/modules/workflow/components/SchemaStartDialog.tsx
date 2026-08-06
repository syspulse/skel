import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { IconClose, IconPlay } from '../../../components/Icons';
import { SliderFieldRow } from '../../../components/SliderFieldRow';

interface SchemaStartDialogProps {
  open: boolean;
  schemaName?: string;   // shown in the title (the schema being started)
  saving: boolean;
  onClose: () => void;
  // input: parsed JSON (undefined when empty -> server uses the default WorkflowConfig payload)
  onStart: (input: unknown | undefined, taskQueue?: string, wid?: string) => void;
}

/** Modal to start a workflow from a WorkflowSchema: input JSON + optional task queue + workflowId. */
export function SchemaStartDialog(props: SchemaStartDialogProps) {
  const { open, schemaName, saving, onClose, onStart } = props;
  const { t } = useTranslation();
  const [inputJson, setInputJson] = useState('');
  const [taskQueue, setTaskQueue] = useState('');
  const [wid, setWid] = useState('');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (open) { setInputJson(''); setTaskQueue(''); setWid(''); setError(null); }
  }, [open]);

  if (!open) return null;

  const handleStart = () => {
    setError(null);
    let input: unknown | undefined = undefined;
    const raw = inputJson.trim();
    if (raw) {
      try { input = JSON.parse(raw); } catch { setError(t('workflow.invalidJson')); return; }
    }
    onStart(input, taskQueue.trim() || undefined, wid.trim() || undefined);
  };

  return (
    <>
      <div className="slider-backdrop" onClick={onClose} />
      <div className="fixed inset-0 z-50 flex items-center justify-center p-4 pointer-events-none">
        <div className="pointer-events-auto w-full max-w-lg bg-card border border-border rounded shadow-lg flex flex-col">
          <div className="slide-header">
            <h2 className="slide-title">
              {t('workflow.startTitle')}{schemaName ? ` / ${schemaName}` : ''}
            </h2>
            <button onClick={onClose} className="slide-close" aria-label={t('common.close')}>
              <IconClose size={18} />
            </button>
          </div>

          <div className="slide-body">
            {error && <div className="alert-error">{error}</div>}

            <div className="field-stack">
              <label className="field-stack-label">{t('workflow.fields.inputJson')}</label>
              <textarea rows={12} spellCheck={false} value={inputJson} onChange={(e) => setInputJson(e.target.value)}
                placeholder={'{\n  "status": "NEW",\n  "cursor": -1,\n  "schema": 1,\n  "steps": [ { "id": 101, "name": "ProofOfReserve", "typ": "AUTO" } ]\n}'}
                className="field-code" />
            </div>

            <SliderFieldRow label={t('workflow.fields.taskQueue')}>
              <input className="field-inline" value={taskQueue} onChange={(e) => setTaskQueue(e.target.value)} placeholder={t('workflow.optional')} />
            </SliderFieldRow>
            <SliderFieldRow label={t('workflow.fields.workflowId')}>
              <input className="field-inline" value={wid} onChange={(e) => setWid(e.target.value)} placeholder={t('workflow.optional')} />
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
