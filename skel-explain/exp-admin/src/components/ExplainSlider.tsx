import React, { useEffect, useState } from 'react';
import type { Explain, ExplainCreateReq, ExplainScript, ExplainUpdateReq } from '../types';
import { MetaEditor } from './MetaEditor';
import { ScriptEditor } from './ScriptEditor';
import { IconClose, IconPlus, IconMinus, IconSave, IconTrash, IconEdit } from './Icons';

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
  // Incremented together with setForm so MetaEditor always remounts with the correct value.
  // React 18 batches both updates → single render with fresh form.meta and new key.
  const [formKey, setFormKey] = useState(0);

  useEffect(() => {
    setError(null);
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

  return (
    <>
      {/* Backdrop (mobile / click-away) */}
      {open && (
        <div
          className="fixed inset-0 z-40 bg-black/10"
          onClick={onClose}
        />
      )}

      {/* Slider panel */}
      <div
        className={`fixed top-14 right-0 bottom-0 w-[880px] max-w-[92vw] bg-white shadow-2xl border-l border-gray-200 z-50 flex flex-col
          transition-transform duration-300 ease-in-out
          ${open ? 'translate-x-0' : 'translate-x-full'}`}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3 border-b border-gray-200 bg-slate-50">
          <h2 className="text-sm font-semibold text-gray-800">
            {addMode ? 'Add New Rule' : 'Edit Rule'}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-700 p-1 rounded transition-colors"
            aria-label="Close"
          >
            <IconClose size={18} />
          </button>
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
          {/* Error */}
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded px-3 py-2">
              {error}
            </div>
          )}

          {/* OID — first field, always visible */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">OID</label>
            {addMode ? (
              <input
                type="text"
                value={form.oid}
                onChange={(e) => setForm((f) => ({ ...f, oid: e.target.value }))}
                placeholder="owner id (leave blank for default)"
                className="w-full text-sm border border-gray-300 rounded px-3 py-1.5 font-mono focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            ) : (
              <div className="text-sm font-mono text-gray-600 bg-gray-50 border border-gray-200 rounded px-3 py-1.5 select-text">
                {form.oid || <span className="text-gray-400 italic">default (empty)</span>}
              </div>
            )}
          </div>

          {/* RID */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">
              RID {addMode && <span className="text-red-500">*</span>}
            </label>
            {addMode ? (
              <input
                type="text"
                value={form.rid}
                onChange={(e) => setForm((f) => ({ ...f, rid: e.target.value }))}
                placeholder="rule identifier"
                className="w-full text-sm border border-gray-300 rounded px-3 py-1.5 font-mono focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            ) : (
              <div className="text-sm font-mono text-gray-700 bg-gray-50 border border-gray-200 rounded px-3 py-1.5 select-text">
                {form.rid}
              </div>
            )}
          </div>

          {/* Name */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">
              Name
            </label>
            <input
              type="text"
              value={form.name}
              onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              placeholder="rule name"
              className="w-full text-sm border border-gray-300 rounded px-3 py-1.5 focus:outline-none focus:ring-1 focus:ring-blue-400"
            />
          </div>

          {/* Description */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">
              Description
            </label>
            <input
              type="text"
              value={form.desc}
              onChange={(e) => setForm((f) => ({ ...f, desc: e.target.value }))}
              placeholder="description"
              className="w-full text-sm border border-gray-300 rounded px-3 py-1.5 focus:outline-none focus:ring-1 focus:ring-blue-400"
            />
          </div>

          {/* SID */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1">
              SID
            </label>
            <input
              type="text"
              value={form.sid}
              onChange={(e) => setForm((f) => ({ ...f, sid: e.target.value }))}
              placeholder="source / session id"
              className="w-full text-sm border border-gray-300 rounded px-3 py-1.5 focus:outline-none focus:ring-1 focus:ring-blue-400"
            />
          </div>

          {/* Scripts */}
          <div>
            <div className="flex items-center justify-between mb-2">
              <label className="text-xs font-semibold text-gray-600 uppercase tracking-wide">
                Scripts
              </label>
              <button
                type="button"
                onClick={handleAddScript}
                className="inline-flex items-center gap-1 text-xs bg-gray-100 hover:bg-gray-200 border border-gray-300 text-gray-700 px-2 py-0.5 rounded transition-colors"
              >
                <IconPlus size={12} /> Script
              </button>
            </div>

            <div className="space-y-3">
              {form.scripts.map((script, idx) => (
                <div
                  key={idx}
                  className="border border-gray-200 rounded p-3 bg-gray-50 space-y-2"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-medium text-gray-500">
                      Script #{idx + 1}
                    </span>
                    <button
                      type="button"
                      onClick={() => handleRemoveScript(idx)}
                      disabled={form.scripts.length <= 1}
                      className="p-0.5 rounded transition-colors disabled:opacity-30 disabled:cursor-not-allowed text-gray-400 hover:text-red-500 hover:enabled:bg-red-50"
                      title={form.scripts.length <= 1 ? 'Cannot remove the only script' : 'Remove script'}
                    >
                      <IconMinus size={14} />
                    </button>
                  </div>

                  {/* Type */}
                  <div className="flex items-center gap-2">
                    <label className="text-xs text-gray-500 w-10">Type:</label>
                    <select
                      value={script.typ}
                      onChange={(e) =>
                        handleScriptChange(idx, 'typ', e.target.value)
                      }
                      className="text-xs border border-gray-300 rounded px-2 py-1 bg-white focus:outline-none focus:ring-1 focus:ring-blue-400"
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
                    <label className="text-xs text-gray-500 block mb-1">
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
                    <label className="text-xs text-gray-500 w-10">Opts:</label>
                    <input
                      type="text"
                      value={script.opts ?? ''}
                      onChange={(e) =>
                        handleScriptChange(idx, 'opts', e.target.value)
                      }
                      placeholder="options"
                      className="flex-1 text-xs border border-gray-300 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-blue-400"
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Meta */}
          <div className="border border-gray-200 rounded p-3 bg-gray-50">
            <MetaEditor
              key={formKey}
              value={form.meta}
              onChange={(meta) => setForm((f) => ({ ...f, meta }))}
            />
          </div>
        </div>

        {/* Footer buttons */}
        <div className="flex items-center gap-2 px-5 py-2.5 border-t border-gray-200 bg-slate-50">
          {addMode ? (
            <>
              <button
                onClick={handleCreate}
                disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <IconSave size={13} />
                {saving ? 'Creating…' : 'Create'}
              </button>
              <button
                onClick={onClose}
                disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1 rounded border border-gray-400 text-gray-600 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
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
                className="inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <IconSave size={13} />
                {saving ? 'Saving…' : 'Update'}
              </button>
              <button
                onClick={handleDelete}
                disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <IconTrash size={13} />
                Delete
              </button>
              <button
                onClick={onClose}
                disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1 rounded border border-gray-400 text-gray-600 hover:bg-gray-100 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
              >
                <IconClose size={13} />
                Cancel
              </button>
            </>
          )}
        </div>
      </div>
    </>
  );
}
