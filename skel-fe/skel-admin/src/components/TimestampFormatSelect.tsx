import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  DEFAULT_TIMESTAMP_FORMAT,
  TIMESTAMP_FORMAT_PRESETS,
  formatTimestamp,
  normalizeTimestampPattern,
} from './timestamp';

const PREVIEW_TS = Date.UTC(2025, 5, 7, 14, 30, 45);
const PREVIEW_TIMEZONE = 'UTC';

interface TimestampFormatSelectProps {
  value: string;
  onChange: (pattern: string) => void;
}

export function TimestampFormatSelect({ value, onChange }: TimestampFormatSelectProps) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState(value);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setDraft(value);
  }, [value]);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  const commit = (pattern: string) => {
    const next = normalizeTimestampPattern(pattern);
    onChange(next);
    setDraft(next);
  };

  const handleSelect = (pattern: string) => {
    commit(pattern);
    setOpen(false);
  };

  const preview = formatTimestamp(PREVIEW_TS, PREVIEW_TIMEZONE, draft);
  const hint = t('settings.timestampFormat.hint');

  return (
    <div className="flex items-center gap-3 min-w-0">
      <div ref={ref} className="relative w-64 shrink-0" title={hint}>
        <div className="flex items-center border border-input rounded bg-card focus-within:ring-1 focus-within:ring-blue-400">
          <input
            type="text"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onBlur={() => commit(draft)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                commit(draft);
                setOpen(false);
              }
              if (e.key === 'Escape') {
                setDraft(value);
                setOpen(false);
              }
            }}
            placeholder={DEFAULT_TIMESTAMP_FORMAT}
            title={hint}
            className="text-xs px-2.5 py-1.5 flex-1 min-w-0 bg-transparent text-foreground focus:outline-none font-mono"
          />
          <button
            type="button"
            onClick={() => setOpen((o) => !o)}
            className="px-1.5 text-muted-foreground hover:text-foreground transition-colors shrink-0 border-l border-input"
            tabIndex={-1}
            title={hint}
            aria-label={t('settings.timestampFormat.presets')}
          >
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M2 4l4 4 4-4" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        </div>

        {open && (
          <div className="absolute z-50 left-0 top-full mt-1 w-full popover overflow-y-auto max-h-48">
            {TIMESTAMP_FORMAT_PRESETS.map(({ pattern }) => (
              <button
                key={pattern}
                type="button"
                onClick={() => handleSelect(pattern)}
                className={`w-full text-left px-2.5 py-1.5 text-xs hover:bg-muted transition-colors${
                  pattern === normalizeTimestampPattern(value) ? ' bg-muted font-medium' : ''
                }`}
              >
                <div className="font-mono text-foreground">{pattern}</div>
                <div className="text-muted-foreground">
                  {formatTimestamp(PREVIEW_TS, PREVIEW_TIMEZONE, pattern)}
                </div>
              </button>
            ))}
          </div>
        )}
      </div>

      <span className="text-xs text-muted-foreground whitespace-nowrap">
        {t('settings.timestampFormat.preview')}: <span className="font-mono">{preview}</span>
      </span>
    </div>
  );
}
