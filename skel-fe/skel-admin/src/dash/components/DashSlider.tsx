import React, { useEffect, useState } from 'react';
import type { DashLayout, DashCreateReq, DashUpdateReq } from '../types';
import { useNotifications } from '../../notifications/NotificationContext';
import { IconClose, IconSave, IconTrash } from '../../components/Icons';

interface DashSliderProps {
  open: boolean;
  addMode: boolean;
  dash: DashLayout | null;
  onClose: () => void;
  onCreate: (req: DashCreateReq) => Promise<void>;
  onUpdate: (id: string, req: DashUpdateReq) => Promise<void>;
  onDelete: (dash: DashLayout) => Promise<void>;
}

function emptyForm() {
  return { name: '', desc: '', tags: '', layoutRaw: '{}' };
}

function dashToForm(dash: DashLayout) {
  return {
    name: dash.name ?? '',
    desc: dash.desc ?? '',
    tags: dash.tags?.join(', ') ?? '',
    layoutRaw: JSON.stringify(dash.layout, null, 2),
  };
}

function parseTags(raw: string): string[] {
  return raw.split(',').map(t => t.trim()).filter(Boolean);
}

export function DashSlider({
  open,
  addMode,
  dash,
  onClose,
  onCreate,
  onUpdate,
  onDelete,
}: DashSliderProps) {
  const { add: notify } = useNotifications();
  const [form, setForm] = useState(emptyForm());
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setError(null);
    if (addMode) {
      setForm(emptyForm());
    } else if (dash) {
      setForm(dashToForm(dash));
    }
  }, [dash, addMode, open]);

  const handleCreate = async () => {
    setSaving(true); setError(null);
    try {
      let layout: Record<string, unknown> = {};
      try {
        layout = JSON.parse(form.layoutRaw.trim() || '{}');
      } catch {
        setError('layout must be valid JSON');
        return;
      }
      const req: DashCreateReq = {
        layout,
        name: form.name || undefined,
        desc: form.desc || undefined,
        tags: parseTags(form.tags).length > 0 ? parseTags(form.tags) : undefined,
      };
      await onCreate(req);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
      notify('error', 'Failed to create dashboard', msg);
    } finally {
      setSaving(false);
    }
  };

  const handleUpdate = async () => {
    if (!dash) return;
    setSaving(true); setError(null);
    try {
      let layout: Record<string, unknown> | undefined;
      if (form.layoutRaw.trim()) {
        try {
          layout = JSON.parse(form.layoutRaw.trim());
        } catch {
          setError('layout must be valid JSON');
          return;
        }
      }
      const req: DashUpdateReq = {
        layout,
        name: form.name || undefined,
        desc: form.desc || undefined,
        tags: parseTags(form.tags).length > 0 ? parseTags(form.tags) : undefined,
      };
      await onUpdate(dash.id, req);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
      notify('error', 'Failed to update dashboard', msg);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!dash) return;
    setSaving(true); setError(null);
    try {
      await onDelete(dash);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
      notify('error', 'Failed to delete dashboard', msg);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      {open && <div className="fixed inset-0 z-40 bg-black/10" onClick={onClose} />}

      <div
        className={`fixed top-12 right-0 bottom-0 w-[640px] max-w-[92vw] bg-card shadow-2xl border-l border-border z-50 flex flex-col
          transition-transform duration-300 ease-in-out
          ${open ? 'translate-x-0' : 'translate-x-full'}`}
      >
        <div className="flex items-center justify-between px-5 py-3 border-b border-border bg-muted">
          <h2 className="text-sm text-foreground">{addMode ? 'Add Dashboard' : 'Edit Dashboard'}</h2>
          <button onClick={onClose} className="text-muted-foreground hover:text-foreground p-1 rounded transition-colors" aria-label="Close">
            <IconClose size={18} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3">
          {error && (
            <div className="bg-red-50 border border-red-200 text-red-700 text-xs rounded px-3 py-2">{error}</div>
          )}

          {!addMode && dash && (
            <div className="flex items-center gap-2">
              <label className="w-20 shrink-0 text-xs text-muted-foreground">id</label>
              <div className="flex-1 text-xs font-mono text-foreground bg-muted border border-border rounded px-3 py-1 select-text truncate">
                {dash.id}
              </div>
            </div>
          )}

          <div className="flex items-center gap-2">
            <label className="w-20 shrink-0 text-xs text-muted-foreground">name</label>
            <input type="text" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              placeholder="dashboard name"
              className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400" />
          </div>

          <div className="flex items-center gap-2">
            <label className="w-20 shrink-0 text-xs text-muted-foreground">desc</label>
            <input type="text" value={form.desc} onChange={(e) => setForm((f) => ({ ...f, desc: e.target.value }))}
              placeholder="description"
              className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400" />
          </div>

          <div className="flex items-center gap-2">
            <label className="w-20 shrink-0 text-xs text-muted-foreground">tags</label>
            <input type="text" value={form.tags} onChange={(e) => setForm((f) => ({ ...f, tags: e.target.value }))}
              placeholder="tag1, tag2, tag3"
              className="flex-1 text-sm border border-input rounded px-3 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400" />
          </div>

          <div>
            <label className="text-xs text-muted-foreground block mb-1">
              layout {addMode && <span className="text-muted-foreground">(JSON)</span>}
            </label>
            <textarea
              value={form.layoutRaw}
              onChange={(e) => setForm((f) => ({ ...f, layoutRaw: e.target.value }))}
              rows={16}
              spellCheck={false}
              placeholder="{}"
              className="w-full text-xs font-mono border border-input rounded px-2 py-1.5 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 resize-y"
            />
          </div>

          {!addMode && dash && (
            <div className="text-[11px] text-muted-foreground space-y-0.5">
              {dash.pid && <div><span className="text-foreground">pid:</span> {dash.pid}</div>}
              {dash.tid && <div><span className="text-foreground">tid:</span> {dash.tid}</div>}
            </div>
          )}
        </div>

        <div className="flex items-center gap-2 px-5 py-2.5 border-t border-border bg-muted">
          {addMode ? (
            <>
              <button onClick={handleCreate} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                <IconSave size={13} />{saving ? 'Creating…' : 'Create'}
              </button>
              <button onClick={onClose} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-card disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                <IconClose size={13} />Cancel
              </button>
            </>
          ) : (
            <>
              <button onClick={handleUpdate} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                <IconSave size={13} />{saving ? 'Saving…' : 'Update'}
              </button>
              <button onClick={handleDelete} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                <IconTrash size={13} />Delete
              </button>
              <button onClick={onClose} disabled={saving}
                className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-card disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
                <IconClose size={13} />Cancel
              </button>
            </>
          )}
        </div>
      </div>
    </>
  );
}
