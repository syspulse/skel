import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Explain, ExplainCreateReq, ExplainRes, ExplainScript, ExplainUpdateReq } from '../types';
import { runExplain } from '../api';
import { useAuth } from '../../auth/useAuth';
import { useNotifications } from '../../notifications/NotificationContext';
import { MetaEditor } from './MetaEditor';
import { ScriptEditor } from '../../components/ScriptEditor';
import { ExplainResultSlider } from './ExplainResultSlider';
import { IconClose, IconPlay, IconPlus, IconMinus, IconSave, IconTrash, IconUpload } from '../../components/Icons';

interface ExplainSliderProps {
  open: boolean;
  addMode: boolean;
  explain: Explain | null;
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

export function ExplainSlider({
  open,
  addMode,
  explain,
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
  const { add: notify } = useNotifications();
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
    if (!form.rid.trim()) { setError('RID is required'); return; }
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
      setError(e instanceof Error ? e.message : String(e));
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
        meta: Object.keys(form.meta).length > 0 ? form.meta : undefined,
      });
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
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
      setError(e instanceof Error ? e.message : String(e));
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
        setTestError(msg);
        notify('error', t('explain.errorRun'), msg);
        return;
      }
      const res = await runExplain(token, explain.rid, parsed, form.oid || undefined, testStyle || undefined);
      setTestResult(res);
      setResultOpen(true);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setTestError(msg);
      notify('error', t('explain.errorRun'), msg);
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
      {open && <div className="fixed inset-0 z-40 bg-black/10" onClick={onClose} />}

      <ExplainResultSlider
        open={resultOpen}
        result={testResult}
        propertiesWidth={880}
        onClose={() => setResultOpen(false)}
      />

      <div
        className={`fixed top-12 right-0 bottom-0 w-[880px] max-w-[92vw] bg-card border-l border-border z-50 flex flex-col
          transition-transform duration-300 ease-in-out pointer-events-none
          ${open ? 'translate-x-0 shadow-2xl pointer-events-auto' : 'translate-x-full shadow-none'}`}
      >
        <div className="flex items-center justify-between px-5 py-3 border-b border-border bg-muted">
          <h2 className="text-sm text-foreground">{addMode ? t('common.add') : t('common.edit')}</h2>
          <button onClick={onClose} className="text-muted-foreground hover:text-foreground p-1 rounded transition-colors" aria-label={t('common.close')}>
            <IconClose size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3">
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded px-3 py-2">{error}</div>
          )}

          <div className="space-y-1.5">
            <div className="flex items-center gap-2">
              <label className="w-24 shrink-0 text-xs text-muted-foreground">{t('explain.fields.oid')}</label>
              {addMode ? (
                <input type="text" value={form.oid} onChange={(e) => setForm((f) => ({ ...f, oid: e.target.value }))}
                  placeholder={t('explain.placeholderOid')}
                  className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground font-mono focus:outline-none focus:ring-1 focus:ring-blue-400" />
              ) : (
                <div className="flex-1 text-sm font-mono text-foreground bg-muted border border-border rounded px-3 py-1 select-text">
                  {form.oid || <span className="text-muted-foreground italic">{t('explain.placeholderOid')}</span>}
                </div>
              )}
            </div>

            <div className="flex items-center gap-2">
              <label className="w-24 shrink-0 text-xs text-muted-foreground">{t('explain.fields.rid')} {addMode && <span className="text-red-500">*</span>}</label>
              {addMode ? (
                <input type="text" value={form.rid} onChange={(e) => setForm((f) => ({ ...f, rid: e.target.value }))}
                  placeholder={t('explain.placeholderRid')}
                  className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground font-mono focus:outline-none focus:ring-1 focus:ring-blue-400" />
              ) : (
                <div className="flex-1 text-sm font-mono text-foreground bg-muted border border-border rounded px-3 py-1 select-text">{form.rid}</div>
              )}
            </div>

            <div className="flex items-center gap-2">
              <label className="w-24 shrink-0 text-xs text-muted-foreground">{t('explain.fields.name')}</label>
              <input type="text" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder={t('explain.placeholderName')}
                className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400" />
            </div>

            <div className="flex items-center gap-2">
              <label className="w-24 shrink-0 text-xs text-muted-foreground">{t('explain.fields.desc')}</label>
              <input type="text" value={form.desc} onChange={(e) => setForm((f) => ({ ...f, desc: e.target.value }))}
                placeholder={t('explain.placeholderDesc')}
                className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400" />
            </div>

            <div className="flex items-center gap-2">
              <label className="w-24 shrink-0 text-xs text-muted-foreground">{t('explain.fields.sid')}</label>
              <input type="text" value={form.sid} onChange={(e) => setForm((f) => ({ ...f, sid: e.target.value }))}
                placeholder={t('explain.placeholderSid')}
                className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400" />
            </div>
          </div>

          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-xs text-muted-foreground">{t('explain.scripts')}</label>
              <button type="button" onClick={handleAddScript}
                className="inline-flex items-center gap-1 text-xs bg-muted hover:bg-muted-hover border border-border text-foreground px-2 py-0.5 rounded transition-colors">
                <IconPlus size={12} /> {t('explain.addScript')}
              </button>
            </div>

            <div className="space-y-3">
              {form.scripts.map((script, idx) => (
                <div key={idx} className="border border-border rounded p-3 bg-muted space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-muted-foreground">{t('explain.scriptLabel', { index: idx + 1 })}</span>
                    <button type="button" onClick={() => handleRemoveScript(idx)}
                      disabled={form.scripts.length <= 1}
                      className="p-0.5 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed text-muted-foreground hover:text-red-500 hover:enabled:bg-red-50"
                      title={form.scripts.length <= 1 ? t('explain.onlyScript') : t('explain.removeScript')}>
                      <IconMinus size={14} />
                    </button>
                  </div>

                  <div className="flex items-center gap-2">
                    <label className="text-xs text-muted-foreground w-10">{t('explain.typ')}</label>
                    <select value={script.typ} onChange={(e) => handleScriptChange(idx, 'typ', e.target.value)}
                      className="text-xs border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400">
                      {SCRIPT_TYPES.map((tp) => <option key={tp} value={tp}>{tp}</option>)}
                    </select>
                  </div>

                  <div>
                    <label className="text-xs text-muted-foreground block mb-1">{t('explain.src')}</label>
                    <ScriptEditor typ={script.typ} value={script.src} onChange={(v) => handleScriptChange(idx, 'src', v)} />
                  </div>

                  <div className="flex items-center gap-2">
                    <label className="text-xs text-muted-foreground w-10">{t('explain.opts')}</label>
                    <input type="text" value={script.opts ?? ''} onChange={(e) => handleScriptChange(idx, 'opts', e.target.value)}
                      placeholder={t('explain.placeholderOpts')}
                      className="flex-1 text-xs border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400" />
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="border border-border rounded p-3 bg-muted">
            <MetaEditor key={formKey} value={form.meta} onChange={(meta) => setForm((f) => ({ ...f, meta }))} />
          </div>

          {!addMode && (
            <div ref={testSectionRef} className="border border-border rounded bg-muted">
              <div className="flex items-center justify-between px-3 py-2 border-b border-border">
                <span className="text-xs text-muted-foreground">{t('explain.testSection')}</span>
                <div className="flex items-center gap-2">
                  <select value={testStyle} onChange={(e) => setTestStyle(e.target.value)}
                    className="text-xs border border-input rounded px-2 py-0.5 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400">
                    <option value="">{t('explain.styleDefault')}</option>
                    <option value="short">{t('explain.styleShort')}</option>
                    <option value="narrative">{t('explain.styleNarrative')}</option>
                    <option value="detailed">{t('explain.styleDetailed')}</option>
                  </select>
                  <button type="button" onClick={() => fileInputRef.current?.click()}
                    className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded border border-border text-muted-foreground hover:bg-card transition-colors">
                    <IconUpload size={12} /> {t('common.loadJson')}
                  </button>
                  <input ref={fileInputRef} type="file" accept=".json,application/json" className="hidden" onChange={handleFileUpload} />
                  <button type="button" onClick={handleExplain} disabled={explaining}
                    className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded border border-green-500 text-green-700 hover:bg-green-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                    <IconPlay size={12} />
                    {explaining ? t('common.running') : t('common.run')}
                  </button>
                </div>
              </div>

              <div className="p-3 space-y-2">
                <textarea value={testData} onChange={(e) => setTestData(e.target.value)}
                  rows={6} placeholder={'{\n  "address": "0x...",\n  "meta": { "balance[ETH]": "1.23" }\n}'}
                  spellCheck={false}
                  className="w-full text-xs font-mono border border-input rounded px-2 py-1.5 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 resize-y" />
                {testError && (
                  <div className="text-xs text-red-600 bg-red-50 border border-red-200 rounded px-2 py-1.5">{testError}</div>
                )}
              </div>
            </div>
          )}
        </div>

        <div className="flex items-center gap-2 px-5 py-2.5 border-t border-border bg-muted">
          {addMode ? (
            <>
              <button onClick={handleCreate} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                <IconSave size={13} />{saving ? t('common.creating') : t('common.create')}
              </button>
              <button onClick={onClose} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-card disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                <IconClose size={13} />{t('common.cancel')}
              </button>
            </>
          ) : (
            <>
              <button onClick={handleUpdate} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                <IconSave size={13} />{saving ? t('common.saving') : t('common.update')}
              </button>
              <button onClick={handleDelete} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                <IconTrash size={13} />{t('common.delete')}
              </button>
              <button onClick={onClose} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-card disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                <IconClose size={13} />{t('common.cancel')}
              </button>
              <div className="flex-1" />
              <button onClick={() => { testSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); handleExplain(); }}
                disabled={explaining || saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-green-500 text-green-700 hover:bg-green-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                <IconPlay size={13} />{explaining ? t('common.running') : t('common.explain')}
              </button>
            </>
          )}
        </div>
      </div>
    </>
  );
}
