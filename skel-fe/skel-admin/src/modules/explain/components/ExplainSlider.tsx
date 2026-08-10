import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Explain, ExplainCreateReq, ExplainRes, ExplainScript, ExplainUpdateReq } from '../types';
import { runExplain } from '../api';
import { useAuth } from '../../../auth/useAuth';
import { useModuleNotify } from '../../../notifications/moduleNotify';
import { MetaEditor } from './MetaEditor';
import { ScriptEditor } from '../../../components/ScriptEditor';
import { ExplainResultSlider } from './ExplainResultSlider';
import { IconClose, IconPlay, IconPlus, IconMinus, IconSave, IconTrash, IconUpload } from '../../../components/Icons';

import { FormattedTimestamp } from '../../../components/FormattedTimestamp';
import { SliderFieldRow } from '../../../components/SliderFieldRow';

interface ExplainSliderProps {
  open: boolean;
  addMode: boolean;
  explain: Explain | null;
  timezone: string;
  onClose: () => void;
  onCreate: (rid: string, req: ExplainCreateReq) => Promise<void>;
  onUpdate: (rid: string, req: ExplainUpdateReq) => Promise<void>;
  onDelete: (explain: Explain) => Promise<void>;
}

const SCRIPT_TYPES = ['js', 'ai', 'jq', 'regexp', 'str'];

function emptyScript(): ExplainScript {
  return { typ: 'str', src: '' };
}

function emptyForm() {
  return {
    oid: '',
    rid: '',
    name: '',
    desc: '',
    sid: '',
    scripts: [emptyScript()] as ExplainScript[],
    meta: {} as Record<string, unknown>,
  };
}

function explainToForm(explain: Explain) {
  return {
    oid: explain.oid ?? '',
    rid: explain.rid,
    name: explain.name ?? '',
    desc: explain.desc ?? '',
    sid: explain.sid ?? '',
    scripts: explain.scripts.length > 0 ? explain.scripts.map((s) => ({ ...s })) : [emptyScript()],
    meta: explain.meta ? { ...explain.meta } : {},
  };
}

function metaJson(meta: Record<string, unknown>): string {
  return JSON.stringify(meta, Object.keys(meta).sort());
}

/** Include meta in update when it changed; empty {} clears existing meta. */
function metaForUpdate(
  formMeta: Record<string, unknown>,
  originalMeta?: Record<string, unknown>,
): Record<string, unknown> | undefined {
  const original = originalMeta ? { ...originalMeta } : {};
  return metaJson(formMeta) !== metaJson(original) ? formMeta : undefined;
}

export function ExplainSlider({
  open,
  addMode,
  explain,
  timezone,
  onClose,
  onCreate,
  onUpdate,
  onDelete,
}: ExplainSliderProps) {
  const { t } = useTranslation();
  const [form, setForm] = useState(emptyForm());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formKey, setFormKey] = useState(0);

  const { token } = useAuth();
  const moduleName = t('nav.explain');
  const { notifyError, moduleErrorMessage } = useModuleNotify(moduleName);
  const [testData, setTestData]     = useState('');
  const [testStyle, setTestStyle]   = useState('');
  const [testResult, setTestResult] = useState<ExplainRes | null>(null);
  const [testError, setTestError]   = useState<string | null>(null);
  const [explaining, setExplaining] = useState(false);
  const [resultOpen, setResultOpen] = useState(false);
  const fileInputRef                = useRef<HTMLInputElement>(null);
  const testSectionRef              = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setError(null);
    if (!open) setResultOpen(false);
    if (addMode) setForm(emptyForm());
    else if (explain) setForm(explainToForm(explain));
    setFormKey((k) => k + 1);
  }, [explain, addMode, open]);

  const handleScriptChange = (idx: number, field: keyof ExplainScript, value: string) => {
    setForm((f) => ({ ...f, scripts: f.scripts.map((s, i) => i === idx ? { ...s, [field]: value } : s) }));
  };

  const handleAddScript = () => {
    setForm((f) => ({ ...f, scripts: [...f.scripts, emptyScript()] }));
  };

  const handleRemoveScript = (idx: number) => {
    if (form.scripts.length <= 1) return;
    setForm((f) => ({ ...f, scripts: f.scripts.filter((_, i) => i !== idx) }));
  };

  const handleCreate = async () => {
    if (!form.rid.trim()) {
      setError(moduleErrorMessage(moduleName, t('explain.errorCreate'), 'RID is required'));
      return;
    }
    setSaving(true); setError(null);
    try {
      await onCreate(form.rid.trim(), {
        oid: form.oid.trim() || undefined,
        scripts: form.scripts,
        name: form.name || undefined,
        desc: form.desc || undefined,
        sid: form.sid || undefined,
        meta: Object.keys(form.meta).length > 0 ? form.meta : undefined,
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(moduleErrorMessage(moduleName, t('explain.errorCreate'), msg));
      notifyError(t('explain.errorCreate'), msg);
    } finally {
      setSaving(false);
    }
  };

  const handleUpdate = async () => {
    if (!explain) return;
    setSaving(true); setError(null);
    try {
      await onUpdate(explain.rid, {
        scripts: form.scripts,
        name: form.name || undefined,
        desc: form.desc || undefined,
        sid: form.sid || undefined,
        meta: metaForUpdate(form.meta, explain.meta),
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(moduleErrorMessage(moduleName, t('explain.errorUpdate'), msg));
      notifyError(t('explain.errorUpdate'), msg);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!explain) return;
    setSaving(true); setError(null);
    try {
      await onDelete(explain);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(moduleErrorMessage(moduleName, t('explain.errorDelete'), msg));
      notifyError(t('explain.errorDelete'), msg);
    } finally {
      setSaving(false);
    }
  };

  const handleExplain = async () => {
    if (!explain) return;
    setExplaining(true); setTestError(null); setTestResult(null);
    testSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    try {
      let parsed: unknown;
      try {
        parsed = testData.trim() ? JSON.parse(testData) : {};
      } catch {
        const msg = t('explain.invalidJson');
        setTestError(moduleErrorMessage(moduleName, t('explain.errorRun'), msg));
        notifyError(t('explain.errorRun'), msg);
        return;
      }
      const res = await runExplain(token, explain.rid, parsed, form.oid || undefined, testStyle || undefined);
      setTestResult(res);
      setResultOpen(true);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setTestError(moduleErrorMessage(moduleName, t('explain.errorRun'), msg));
      notifyError(t('explain.errorRun'), msg);
    } finally {
      setExplaining(false);
    }
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (ev) => setTestData(ev.target?.result as string ?? '');
    reader.readAsText(file);
    e.target.value = '';
  };

  return (
    <>
      {open && <div className="slider-backdrop" onClick={onClose} />}

      <ExplainResultSlider
        open={resultOpen}
        result={testResult}
        propertiesWidth={880}
        onClose={() => setResultOpen(false)}
      />

      <div className={`slide-panel slide-panel-lg ${open ? 'slide-panel-open' : 'slide-panel-closed'}`}>
        <div className="slide-header">
          <h2 className="slide-title">{addMode ? t('common.add') : t('common.edit')}</h2>
          <button onClick={onClose} className="slide-close" aria-label={t('common.close')}>
            <IconClose size={18} />
          </button>
        </div>

        <div className="slide-body">
          {error && <div className="alert-error">{error}</div>}

          <div className="slide-fields">
            <SliderFieldRow label={t('explain.fields.oid')}>
              {addMode ? (
                <input type="text" value={form.oid} onChange={(e) => setForm((f) => ({ ...f, oid: e.target.value }))}
                  placeholder={t('explain.placeholderOid')} className="field-inline-mono" />
              ) : (
                <div className="field-readonly-mono">
                  {form.oid || <span className="text-muted-foreground italic">{t('explain.placeholderOid')}</span>}
                </div>
              )}
            </SliderFieldRow>

            <SliderFieldRow label={<>{t('explain.fields.rid')} {addMode && <span className="text-red-500">*</span>}</>}>
              {addMode ? (
                <input type="text" value={form.rid} onChange={(e) => setForm((f) => ({ ...f, rid: e.target.value }))}
                  placeholder={t('explain.placeholderRid')} className="field-inline-mono" />
              ) : (
                <div className="field-readonly-mono">{form.rid}</div>
              )}
            </SliderFieldRow>

            <SliderFieldRow label={t('explain.fields.name')}>
              <input type="text" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder={t('explain.placeholderName')} className="field-inline" />
            </SliderFieldRow>

            <SliderFieldRow label={t('explain.fields.desc')}>
              <input type="text" value={form.desc} onChange={(e) => setForm((f) => ({ ...f, desc: e.target.value }))}
                placeholder={t('explain.placeholderDesc')} className="field-inline" />
            </SliderFieldRow>

            <SliderFieldRow label={t('explain.fields.sid')}>
              <input type="text" value={form.sid} onChange={(e) => setForm((f) => ({ ...f, sid: e.target.value }))}
                placeholder={t('explain.placeholderSid')} className="field-inline" />
            </SliderFieldRow>

            {!addMode && explain && (
              <>
                <SliderFieldRow label={t('explain.fields.ts0')}>
                  <div className="field-readonly-mono-muted"><FormattedTimestamp ts={explain.ts0} timezone={timezone} /></div>
                </SliderFieldRow>
                <SliderFieldRow label={t('explain.fields.ts')}>
                  <div className="field-readonly-mono-muted"><FormattedTimestamp ts={explain.ts} timezone={timezone} /></div>
                </SliderFieldRow>
              </>
            )}
          </div>

          <div>
            <div className="slide-section-header mb-2">
              <label className="field-stack-label">{t('explain.scripts')}</label>
              <button type="button" onClick={handleAddScript} className="btn-compact">
                <IconPlus size={12} /> {t('explain.addScript')}
              </button>
            </div>

            <div className="space-y-3">
              {form.scripts.map((script, idx) => (
                <div key={idx} className="slide-card">
                  <div className="flex items-center justify-between">
                    <span className="field-stack-label">{t('explain.scriptLabel', { index: idx + 1 })}</span>
                    <button type="button" onClick={() => handleRemoveScript(idx)}
                      disabled={form.scripts.length <= 1}
                      className="btn-icon-danger"
                      title={form.scripts.length <= 1 ? t('explain.onlyScript') : t('explain.removeScript')}>
                      <IconMinus size={14} />
                    </button>
                  </div>

                  <SliderFieldRow label={t('explain.typ')} labelWidth="10">
                    <select value={script.typ} onChange={(e) => handleScriptChange(idx, 'typ', e.target.value)}
                      className="field-compact">
                      {SCRIPT_TYPES.map((tp) => <option key={tp} value={tp}>{tp}</option>)}
                    </select>
                  </SliderFieldRow>

                  <div>
                    <label className="field-stack-label block mb-1">{t('explain.src')}</label>
                    <ScriptEditor typ={script.typ} value={script.src} onChange={(v) => handleScriptChange(idx, 'src', v)} />
                  </div>

                  <SliderFieldRow label={t('explain.opts')} labelWidth="10">
                    <input type="text" value={script.opts ?? ''} onChange={(e) => handleScriptChange(idx, 'opts', e.target.value)}
                      placeholder={t('explain.placeholderOpts')}
                      className="field-compact flex-1" />
                  </SliderFieldRow>
                </div>
              ))}
            </div>
          </div>

          <MetaEditor key={formKey} value={form.meta} onChange={(meta) => setForm((f) => ({ ...f, meta }))} />

          {!addMode && (
            <div ref={testSectionRef} className="slide-section">
              <div className="slide-section-header">
                <span className="field-stack-label">{t('explain.testSection')}</span>
                <div className="flex items-center gap-2">
                  <select value={testStyle} onChange={(e) => setTestStyle(e.target.value)}
                    className="field-compact">
                    <option value="">{t('explain.styleDefault')}</option>
                    <option value="short">{t('explain.styleShort')}</option>
                    <option value="narrative">{t('explain.styleNarrative')}</option>
                    <option value="detailed">{t('explain.styleDetailed')}</option>
                  </select>
                  <button type="button" onClick={() => fileInputRef.current?.click()} className="btn-load">
                    <IconUpload size={12} /> {t('common.loadJson')}
                  </button>
                  <input ref={fileInputRef} type="file" accept=".json,application/json" className="hidden" onChange={handleFileUpload} />
                  <button type="button" onClick={handleExplain} disabled={explaining} className="btn-run-sm">
                    <IconPlay size={12} />
                    {explaining ? t('common.running') : t('common.run')}
                  </button>
                </div>
              </div>

              <div className="p-3 space-y-2">
                <textarea value={testData} onChange={(e) => setTestData(e.target.value)}
                  rows={6} placeholder={'{\n  "address": "0x...",\n  "meta": { "balance[ETH]": "1.23" }\n}'}
                  spellCheck={false}
                  className="field-code" />
                {testError && <div className="alert-error-inline">{testError}</div>}
              </div>
            </div>
          )}
        </div>

        <div className="slide-footer">
          {addMode ? (
            <>
              <button onClick={handleCreate} disabled={saving} className="btn-add">
                <IconSave size={13} />{saving ? t('common.creating') : t('common.create')}
              </button>
              <button onClick={onClose} disabled={saving} className="btn-cancel">
                <IconClose size={13} />{t('common.cancel')}
              </button>
            </>
          ) : (
            <>
              <button onClick={handleUpdate} disabled={saving} className="btn-save">
                <IconSave size={13} />{saving ? t('common.saving') : t('common.update')}
              </button>
              <button onClick={handleDelete} disabled={saving} className="btn-danger">
                <IconTrash size={13} />{t('common.delete')}
              </button>
              <button onClick={onClose} disabled={saving} className="btn-cancel">
                <IconClose size={13} />{t('common.cancel')}
              </button>
              <div className="flex-1" />
              <button onClick={() => { testSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); handleExplain(); }}
                disabled={explaining || saving} className="btn-run">
                <IconPlay size={13} />{explaining ? t('common.running') : t('common.explain')}
              </button>
            </>
          )}
        </div>
      </div>
    </>
  );
}
