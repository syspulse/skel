import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { IconClose, IconCopy } from '../../components/Icons';
import { JsonCodeEditor } from '../workflow/components/SchemaConfigEditor';

type TabId = 'serialize' | 'deserialize';

interface JsonToolModalProps {
  open: boolean;
  onClose: () => void;
}

function serializeJson(src: string): { text: string; error: string | null } {
  try {
    const compact = JSON.stringify(JSON.parse(src));
    return { text: JSON.stringify(compact), error: null };
  } catch {
    return { text: '', error: 'invalid' };
  }
}

function deserializeJson(src: string): { text: string; error: string | null } {
  try {
    const v = JSON.parse(src);
    const jsonText = typeof v === 'string' ? v : JSON.stringify(v);
    return { text: JSON.stringify(JSON.parse(jsonText), null, 2), error: null };
  } catch {
    return { text: '', error: 'invalid' };
  }
}

export function JsonToolModal({ open, onClose }: JsonToolModalProps) {
  const { t } = useTranslation();
  const [tab, setTab] = useState<TabId>('serialize');
  const [jsonText, setJsonText] = useState('{\n  \n}');
  const [stringText, setStringText] = useState('');
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (open) {
      setTab('serialize');
      setJsonText('{\n  \n}');
      setStringText('');
      setCopied(false);
    }
  }, [open]);

  const serialized = useMemo(() => serializeJson(jsonText), [jsonText]);
  const deserialized = useMemo(() => deserializeJson(stringText), [stringText]);

  if (!open) return null;

  const copySerialized = async () => {
    if (!serialized.text) return;
    try {
      await navigator.clipboard.writeText(serialized.text);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch { /* clipboard unavailable */ }
  };

  const tabs: { id: TabId; label: string }[] = [
    { id: 'serialize', label: t('tools.serialize') },
    { id: 'deserialize', label: t('tools.deserialize') },
  ];

  return (
    <>
      <div className="fixed inset-0 z-[90] bg-black/10" onClick={onClose} />
      <div className="fixed inset-0 z-[90] flex items-center justify-center p-4 pointer-events-none">
        <div className="pointer-events-auto w-full max-w-2xl max-h-[90vh] bg-card border border-border rounded shadow-lg flex flex-col">
          <div className="slide-header">
            <h2 className="slide-title">{t('tools.json')}</h2>
            <button onClick={onClose} className="slide-close" aria-label={t('common.close')}>
              <IconClose size={18} />
            </button>
          </div>

          <div className="flex border-b border-border gap-1 px-5 pt-2 shrink-0">
            {tabs.map(({ id, label }) => (
              <button
                key={id}
                type="button"
                onClick={() => setTab(id)}
                className={`px-3 py-1 text-xs transition-colors border-b-2 -mb-px
                  ${tab === id
                    ? 'border-blue-500 text-foreground'
                    : 'border-transparent text-muted-foreground hover:text-foreground'}`}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="slide-body">
            {tab === 'serialize' && (
              <>
                <div className="field-stack">
                  <label className="field-stack-label">{t('tools.jsonInput')}</label>
                  <JsonCodeEditor value={jsonText} onChange={setJsonText} height={180} />
                  {serialized.error && <div className="alert-error">{t('tools.invalidJson')}</div>}
                </div>
                <div className="field-stack">
                  <div className="flex items-center justify-between">
                    <label className="field-stack-label">{t('tools.stringOutput')}</label>
                    <button
                      type="button"
                      onClick={copySerialized}
                      disabled={!serialized.text}
                      className="text-muted-foreground hover:text-foreground p-0.5 rounded transition-colors disabled:opacity-30"
                      aria-label={copied ? t('common.copied') : t('common.copy')}
                      title={copied ? t('common.copied') : t('common.copy')}
                    >
                      <IconCopy size={14} />
                    </button>
                  </div>
                  <textarea
                    rows={6}
                    readOnly
                    spellCheck={false}
                    value={serialized.text}
                    className="field-code-muted"
                  />
                </div>
              </>
            )}

            {tab === 'deserialize' && (
              <>
                <div className="field-stack">
                  <label className="field-stack-label">{t('tools.stringInput')}</label>
                  <textarea
                    rows={6}
                    spellCheck={false}
                    value={stringText}
                    onChange={(e) => setStringText(e.target.value)}
                    placeholder={'"{\\"k\\":\\"v\\"}"'}
                    className="field-code"
                  />
                  {stringText.trim() !== '' && deserialized.error && (
                    <div className="alert-error">{t('tools.invalidJson')}</div>
                  )}
                </div>
                <div className="field-stack">
                  <label className="field-stack-label">{t('tools.jsonOutput')}</label>
                  <JsonCodeEditor value={deserialized.text || '{\n}'} readOnly height={180} />
                </div>
              </>
            )}
          </div>

          <div className="slide-footer">
            <button onClick={onClose} className="btn-add">{t('common.ok')}</button>
          </div>
        </div>
      </div>
    </>
  );
}
