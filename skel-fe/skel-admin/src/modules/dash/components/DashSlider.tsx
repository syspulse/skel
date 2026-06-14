import React, { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { DashLayout, DashCreateReq, DashUpdateReq } from '../types';
import { useModuleNotify } from '../../../notifications/moduleNotify';
import { IconClose, IconSave, IconTrash } from '../../../components/Icons';
import { SliderFieldRow } from '../../../components/SliderFieldRow';

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
  const { t } = useTranslation();
  const moduleName = t('nav.dash');
  const { notifyError, moduleErrorMessage } = useModuleNotify(moduleName);
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
        const err = t('dash.errorLayoutJson');
        setError(moduleErrorMessage(moduleName, err));
        notifyError(err, err);
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
      setError(moduleErrorMessage(moduleName, t('dash.errorCreate'), msg));
      notifyError(t('dash.errorCreate'), msg);
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
          const err = t('dash.errorLayoutJson');
          setError(moduleErrorMessage(moduleName, err));
          notifyError(err, err);
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
      setError(moduleErrorMessage(moduleName, t('dash.errorUpdate'), msg));
      notifyError(t('dash.errorUpdate'), msg);
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
      setError(moduleErrorMessage(moduleName, t('dash.errorDelete'), msg));
      notifyError(t('dash.errorDelete'), msg);
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      {open && <div className="slider-backdrop" onClick={onClose} />}

      <div className={`slide-panel slide-panel-md ${open ? 'slide-panel-open' : 'slide-panel-closed'}`}>
        <div className="slide-header">
          <h2 className="slide-title">{addMode ? t('common.add') : t('common.edit')}</h2>
          <button onClick={onClose} className="slide-close" aria-label={t('common.close')}>
            <IconClose size={18} />
          </button>
        </div>

        <div className="slide-body">
          {error && <div className="alert-error">{error}</div>}

          <div className="slide-fields">
            {!addMode && dash && (
              <SliderFieldRow label={t('dash.fields.id')} labelWidth="20">
                <div className="field-readonly-mono-sm">{dash.id}</div>
              </SliderFieldRow>
            )}

            <SliderFieldRow label={t('dash.fields.name')} labelWidth="20">
              <input type="text" value={form.name} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder={t('dash.placeholderName')} className="field-inline" />
            </SliderFieldRow>

            <SliderFieldRow label={t('dash.fields.desc')} labelWidth="20">
              <input type="text" value={form.desc} onChange={(e) => setForm((f) => ({ ...f, desc: e.target.value }))}
                placeholder={t('dash.placeholderDesc')} className="field-inline" />
            </SliderFieldRow>

            <SliderFieldRow label={t('dash.fields.tags')} labelWidth="20">
              <input type="text" value={form.tags} onChange={(e) => setForm((f) => ({ ...f, tags: e.target.value }))}
                placeholder={t('dash.placeholderTags')} className="field-inline" />
            </SliderFieldRow>
          </div>

          <div className="field-stack">
            <label className="field-stack-label">
              {t('dash.fields.layout')} {addMode && <span className="text-muted-foreground">(JSON)</span>}
            </label>
            <textarea
              value={form.layoutRaw}
              onChange={(e) => setForm((f) => ({ ...f, layoutRaw: e.target.value }))}
              rows={16}
              spellCheck={false}
              placeholder="{}"
              className="field-code"
            />
          </div>

          {!addMode && dash && (
            <div className="slide-meta">
              {dash.pid && <div><span className="slide-meta-key">{t('dash.fields.pid')}:</span> {dash.pid}</div>}
              {dash.tid && <div><span className="slide-meta-key">{t('dash.fields.tid')}:</span> {dash.tid}</div>}
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
              <button onClick={handleUpdate} disabled={saving} className="btn-add">
                <IconSave size={13} />{saving ? t('common.saving') : t('common.update')}
              </button>
              <button onClick={handleDelete} disabled={saving} className="btn-danger">
                <IconTrash size={13} />{t('common.delete')}
              </button>
              <button onClick={onClose} disabled={saving} className="btn-cancel">
                <IconClose size={13} />{t('common.cancel')}
              </button>
            </>
          )}
        </div>
      </div>
    </>
  );
}
