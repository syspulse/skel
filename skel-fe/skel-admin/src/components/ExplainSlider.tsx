import React, { useEffect, useRef, useState } from 'react';
import type { Explain, ExplainCreateReq, ExplainRes, ExplainScript, ExplainUpdateReq } from '../types';
import { runExplain } from '../api';
import { useAuth } from '../auth/useAuth';
import { MetaEditor } from './MetaEditor';
import { ScriptEditor } from './ScriptEditor';
import { ExplainResultSlider } from './ExplainResultSlider';
import { IconClose, IconPlay, IconPlus, IconMinus, IconSave, IconTrash, IconUpload } from './Icons';

interface ExplainSliderProps {
  open: boolean;
  addMode: boolean;
  rule: Explain | null;
  onClose: () => void;
  onCreate: (rid: string, req: ExplainCreateReq) => Promise<void>;
  onUpdate: (rid: string, req: ExplainUpdateReq) => Promise<void>;
  onDelete: (rule: Explain) => Promise<void>;
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

function ruleToForm(rule: Explain) {
  return {
    oid: rule.oid ?? '',
    rid: rule.rid,
    name: rule.name ?? '',
    desc: rule.desc ?? '',
    sid: rule.sid ?? '',
    scripts:
      rule.scripts.length > 0 ? rule.scripts.map((s) => ({ ...s })) : [emptyScript()],
    meta: rule.meta ? { ...rule.meta } : {},
  };
}

export function ExplainSlider({
  open,
  addMode,
  rule,
  onClose,
  onCreate,
  onUpdate,
  onDelete,
}: ExplainSliderProps) {
  const [form, setForm] = useState(emptyForm());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formKey, setFormKey] = useState(0);

  const { token } = useAuth();
  const [testData, setTestData]         = useState('');
  const [testStyle, setTestStyle]       = useState('');
  const [testResult, setTestResult]     = useState<ExplainRes | null>(null);
  const [testError, setTestError]       = useState<string | null>(null);
  const [explaining, setExplaining]     = useState(false);
  const [resultOpen, setResultOpen]     = useState(false);
  const fileInputRef                    = useRef<HTMLInputElement>(null);
  const testSectionRef                  = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setError(null);
    if (!open) {
      setResultOpen(false);
    }
    if (addMode) {
      setForm(emptyForm());
    } else if (rule) {
      setForm(ruleToForm(rule));
    }
    setFormKey((k) => k + 1);
  }, [rule, addMode, open]);

  const handleScriptChange = (
    idx: number,
    field: keyof ExplainScript,
    value: string,
  ) => {
    const newScripts = form.scripts.map((s, i) =>
      i === idx ? { ...s, [field]: value } : s,
    );
    setForm((f) => ({ ...f, scripts: newScripts }));
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
      setError('RID is required');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const req: ExplainCreateReq = {
        oid: form.oid.trim() || undefined,
        scripts: form.scripts,
        name: form.name || undefined,
        desc: form.desc || undefined,
        sid: form.sid || undefined,
        meta: Object.keys(form.meta).length > 0 ? form.meta : undefined,
      };
      await onCreate(form.rid.trim(), req);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  const handleUpdate = async () => {
    if (!rule) return;
    setSaving(true);
    setError(null);
    try {
      const req: ExplainUpdateReq = {
        scripts: form.scripts,
        name: form.name || undefined,
        desc: form.desc || undefined,
        sid: form.sid || undefined,
        meta: Object.keys(form.meta).length > 0 ? form.meta : undefined,
      };
      await onUpdate(rule.rid, req);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!rule) return;
    setSaving(true);
    setError(null);
    try {
      await onDelete(rule);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setSaving(false);
    }
  };

  const handleExplain = async () => {
    if (!rule) return;
    setExplaining(true);
    setTestError(null);
    setTestResult(null);
    testSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    try {
      let parsed: unknown;
      try {
        parsed = testData.trim() ? JSON.parse(testData) : {};
      } catch {
        setTestError('Invalid JSON in input data');
        return;
      }
      const res = await runExplain(token, rule.rid, parsed, form.oid || undefined, testStyle || undefined);
      setTestResult(res);
      setResultOpen(true);
    } catch (e) {
      setTestError(e instanceof Error ? e.message : String(e));
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
      {open && (
        <div
          className="fixed inset-0 z-40 bg-black/10"
          onClick={onClose}
        />
      )}

      <ExplainResultSlider
        open={resultOpen}
        result={testResult}
        propertiesWidth={880}
        onClose={() => setResultOpen(false)}
      />

      {/* Slider panel */}
      <div
        className={`fixed top-14 right-0 bottom-0 w-[880px] max-w-[92vw] bg-card shadow-2xl border-l border-border z-50 flex flex-col
          transition-transform duration-300 ease-in-out
          ${open ? 'translate-x-0' : 'translate-x-full'}`}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-border bg-muted">
          <h2 className="text-sm text-foreground">
            {addMode ? 'Add New Rule' : 'Edit Rule'}
          </h2>
          <button
            onClick={onClose}
            className="text-muted-foreground hover:text-foreground p-1 rounded transition-colors"
            aria-label="Close"
          >
            <IconClose size={18} />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3">
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded px-3 py-2">
              {error}
            </div>
          )}

          {/* Inline fields */}
          <div className="space-y-1.5">
            {/* OID */}
            <div className="flex items-center gap-2">
              <label className="w-24 shrink-0 text-xs text-muted-foreground">OID</label>
              {addMode ? (
                <input
                  type="text"
                  value={form.oid}
                  onChange={(e) => setForm((f) => ({ ...f, oid: e.target.value }))}
                  placeholder="owner id (leave blank for default)"
                  className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground font-mono focus:outline-none focus:ring-1 focus:ring-blue-400"
                />
              ) : (
                <div className="flex-1 text-sm font-mono text-foreground bg-muted border border-border rounded px-3 py-1 select-text">
                  {form.oid || <span className="text-muted-foreground italic">default (empty)</span>}
                </div>
              )}
            </div>

            {/* RID */}
            <div className="flex items-center gap-2">
              <label className="w-24 shrink-0 text-xs text-muted-foreground">
                RID {addMode && <span className="text-red-500">*</span>}
              </label>
              {addMode ? (
                <input
                  type="text"
                  value={form.rid}
                  onChange={(e) => setForm((f) => ({ ...f, rid: e.target.value }))}
                  placeholder="rule identifier"
                  className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground font-mono focus:outline-none focus:ring-1 focus:ring-blue-400"
                />
              ) : (
                <div className="flex-1 text-sm font-mono text-foreground bg-muted border border-border rounded px-3 py-1 select-text">
                  {form.rid}
                </div>
              )}
            </div>

            {/* Name */}
            <div className="flex items-center gap-2">
              <label className="w-24 shrink-0 text-xs text-muted-foreground">Name</label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="rule name"
                className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            </div>

            {/* Description */}
            <div className="flex items-center gap-2">
              <label className="w-24 shrink-0 text-xs text-muted-foreground">Description</label>
              <input
                type="text"
                value={form.desc}
                onChange={(e) => setForm((f) => ({ ...f, desc: e.target.value }))}
                placeholder="description"
                className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            </div>

            {/* SID */}
            <div className="flex items-center gap-2">
              <label className="w-24 shrink-0 text-xs text-muted-foreground">SID</label>
              <input
                type="text"
                value={form.sid}
                onChange={(e) => setForm((f) => ({ ...f, sid: e.target.value }))}
                placeholder="source / session id"
                className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            </div>
          </div>

          {/* Scripts */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-xs text-muted-foreground uppercase tracking-wide">
                Scripts
              </label>
              <button
                type="button"
                onClick={handleAddScript}
                className="inline-flex items-center gap-1 text-xs bg-muted hover:bg-muted-hover border border-border text-foreground px-2 py-0.5 rounded transition-colors"
              >
                <IconPlus size={12} /> Script
              </button>
            </div>

            <div className="space-y-3">
              {form.scripts.map((script, idx) => (
                <div
                  key={idx}
                  className="border border-border rounded p-3 bg-muted space-y-2"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-muted-foreground">
                      Script #{idx + 1}
                    </span>
                    <button
                      type="button"
                      onClick={() => handleRemoveScript(idx)}
                      disabled={form.scripts.length <= 1}
                      className="p-0.5 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed text-muted-foreground hover:text-red-500 hover:enabled:bg-red-50"
                      title={form.scripts.length <= 1 ? 'Cannot remove the only script' : 'Remove script'}
                    >
                      <IconMinus size={14} />
                    </button>
                  </div>

                  {/* Type */}
                  <div className="flex items-center gap-2">
                    <label className="text-xs text-muted-foreground w-10">Type:</label>
                    <select
                      value={script.typ}
                      onChange={(e) =>
                        handleScriptChange(idx, 'typ', e.target.value)
                      }
                      className="text-xs border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
                    >
                      {SCRIPT_TYPES.map((t) => (
                        <option key={t} value={t}>
                          {t}
                        </option>
                      ))}
                    </select>
                  </div>

                  {/* Source */}
                  <div>
                    <label className="text-xs text-muted-foreground block mb-1">
                      Source:
                    </label>
                    <ScriptEditor
                      typ={script.typ}
                      value={script.src}
                      onChange={(v) => handleScriptChange(idx, 'src', v)}
                    />
                  </div>

                  {/* Opts */}
                  <div className="flex items-center gap-2">
                    <label className="text-xs text-muted-foreground w-10">Opts:</label>
                    <input
                      type="text"
                      value={script.opts ?? ''}
                      onChange={(e) =>
                        handleScriptChange(idx, 'opts', e.target.value)
                      }
                      placeholder="options"
                      className="flex-1 text-xs border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Meta */}
          <div className="border border-border rounded p-3 bg-muted">
            <MetaEditor
              key={formKey}
              value={form.meta}
              onChange={(meta) => setForm((f) => ({ ...f, meta }))}
            />
          </div>

          {/* Test Explain */}
          {!addMode && (
            <div ref={testSectionRef} className="border border-border rounded bg-muted">
              <div className="flex items-center justify-between px-3 py-2 border-b border-border">
                <span className="text-xs text-muted-foreground uppercase tracking-wide">
                  Test Explain
                </span>
                <div className="flex items-center gap-2">
                  <select
                    value={testStyle}
                    onChange={(e) => setTestStyle(e.target.value)}
                    className="text-xs border border-input rounded px-2 py-0.5 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
                    title="Explanation style"
                  >
                    <option value="">default</option>
                    <option value="short">short</option>
                    <option value="narrative">narrative</option>
                    <option value="detailed">detailed</option>
                  </select>
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded border border-border text-muted-foreground hover:bg-card transition-colors"
                    title="Load Alert JSON file"
                  >
                    <IconUpload size={12} /> Load JSON
                  </button>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".json,application/json"
                    className="hidden"
                    onChange={handleFileUpload}
                  />
                  <button
                    type="button"
                    onClick={handleExplain}
                    disabled={explaining}
                    className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded border border-green-500 text-green-700 hover:bg-green-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                  >
                    <IconPlay size={12} />
                    {explaining ? 'Running…' : 'Run'}
                  </button>
                </div>
              </div>

              <div className="p-3 space-y-2">
                <textarea
                  value={testData}
                  onChange={(e) => setTestData(e.target.value)}
                  rows={6}
                  placeholder={'{\n  "address": "0x...",\n  "meta": { "balance[ETH]": "1.23" }\n}'}
                  spellCheck={false}
                  className="w-full text-xs font-mono border border-input rounded px-2 py-1.5 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 resize-y"
                />

                {testError && (
                  <div className="text-xs text-red-600 bg-red-50 border border-red-200 rounded px-2 py-1.5">
                    {testError}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center gap-2 px-5 py-2.5 border-t border-border bg-muted">
          {addMode ? (
            <>
              <button
                onClick={handleCreate}
                disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <IconSave size={13} />
                {saving ? 'Creating…' : 'Create'}
              </button>
              <button
                onClick={onClose}
                disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-card disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <IconClose size={13} />
                Cancel
              </button>
            </>
          ) : (
            <>
              <button
                onClick={handleUpdate}
                disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <IconSave size={13} />
                {saving ? 'Saving…' : 'Update'}
              </button>
              <button
                onClick={handleDelete}
                disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <IconTrash size={13} />
                Delete
              </button>
              <button
                onClick={onClose}
                disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-card disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <IconClose size={13} />
                Cancel
              </button>
              <div className="flex-1" />
              <button
                onClick={() => {
                  testSectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                  handleExplain();
                }}
                disabled={explaining || saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-green-500 text-green-700 hover:bg-green-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <IconPlay size={13} />
                {explaining ? 'Running…' : 'Explain'}
              </button>
            </>
          )}
        </div>
      </div>
    </>
  );
}
