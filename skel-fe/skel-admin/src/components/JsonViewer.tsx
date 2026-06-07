import React, { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import CodeMirror from '@uiw/react-codemirror';
import { json } from '@codemirror/lang-json';
import { EditorView } from '@codemirror/view';
import { IconCopy } from './Icons';

export type JsonFormat = 'compact' | 'human';

const VISIBLE_LINES = 15;
const LINE_HEIGHT_PX = 20;
const VIEWER_HEIGHT = `${VISIBLE_LINES * LINE_HEIGHT_PX + 4}px`;

function formatJson(value: unknown, mode: JsonFormat): string {
  if (value === undefined) return '';
  try {
    return mode === 'human'
      ? JSON.stringify(value, null, 2)
      : JSON.stringify(value);
  } catch {
    return String(value);
  }
}

interface JsonViewerProps {
  value: unknown;
  className?: string;
}

export function JsonViewer({ value, className = '' }: JsonViewerProps) {
  const { t } = useTranslation();
  const [format, setFormat] = useState<JsonFormat>('human');
  const [copied, setCopied] = useState(false);

  const text = useMemo(() => formatJson(value, format), [value, format]);

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1500);
    } catch {
      /* clipboard unavailable */
    }
  };

  return (
    <div className={`border border-border rounded bg-muted ${className}`}>
      <div className="flex items-center justify-between px-3 py-1.5 border-b border-border gap-2">
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setFormat('compact')}
            className={`text-[10px] px-1.5 py-0.5 rounded border transition-colors ${
              format === 'compact'
                ? 'bg-card border-border text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            {t('json.compact')}
          </button>
          <button
            type="button"
            onClick={() => setFormat('human')}
            className={`text-[10px] px-1.5 py-0.5 rounded border transition-colors ${
              format === 'human'
                ? 'bg-card border-border text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            {t('json.human')}
          </button>
        </div>
        <button
          type="button"
          onClick={handleCopy}
          className="text-muted-foreground hover:text-foreground p-0.5 rounded transition-colors shrink-0"
          aria-label={copied ? t('common.copied') : t('common.copy')}
          title={copied ? t('common.copied') : t('common.copy')}
        >
          <IconCopy size={13} />
        </button>
      </div>
      <CodeMirror
        value={text || '{}'}
        extensions={[json(), EditorView.lineWrapping, EditorView.editable.of(false)]}
        height={VIEWER_HEIGHT}
        style={{ fontSize: '12px' }}
        basicSetup={{
          lineNumbers: true,
          foldGutter: true,
          dropCursor: false,
          allowMultipleSelections: false,
          indentOnInput: false,
          syntaxHighlighting: true,
          autocompletion: false,
          closeBrackets: false,
        }}
      />
    </div>
  );
}
