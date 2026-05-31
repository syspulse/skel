import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { ExplainRes } from '../types';
import { IconClose } from './Icons';

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

function formatTs(ts: number): string {
  const d = new Date(ts);
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()} `
    + `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:${String(d.getSeconds()).padStart(2,'0')}`;
}

function resolveWidth(meta?: Record<string, unknown>): string {
  const w = meta?.width;
  if (typeof w === 'number') return `${w}px`;
  if (typeof w === 'string' && w.trim()) {
    const s = w.trim();
    return /^\d+$/.test(s) ? `${s}px` : s;
  }
  return '560px';
}

interface ExplainResultSliderProps {
  open: boolean;
  result: ExplainRes | null;
  propertiesWidth?: number;
  onClose: () => void;
}

export function ExplainResultSlider({
  open,
  result,
  propertiesWidth = 880,
  onClose,
}: ExplainResultSliderProps) {
  const width = resolveWidth(result?.meta);
  const metaWithoutWidth = result?.meta
    ? Object.fromEntries(Object.entries(result.meta).filter(([k]) => k !== 'width'))
    : null;

  const transform = open
    ? 'translateX(0)'
    : `translateX(calc(100% + ${propertiesWidth}px))`;

  return (
    <div
      style={{ right: propertiesWidth, width, transform }}
      className="fixed top-14 bottom-0 bg-card shadow-2xl border-l border-border z-[49] flex flex-col transition-transform duration-300 ease-in-out"
    >
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-border bg-muted shrink-0">
        <h2 className="text-sm text-foreground flex items-center gap-2 min-w-0">
          Explanation
          {result?.rid && (
            <span className="font-mono text-xs font-normal text-muted-foreground truncate">{result.rid}</span>
          )}
        </h2>
        <button
          onClick={onClose}
          className="text-muted-foreground hover:text-foreground p-1 rounded transition-colors shrink-0"
          aria-label="Close"
        >
          <IconClose size={18} />
        </button>
      </div>

      {/* Body */}
      <div className="flex-1 overflow-y-auto px-4 py-4 flex flex-col gap-4">
        {result ? (
          <>
            <div className="border border-border rounded px-4 py-3 prose prose-sm max-w-none overflow-auto">
              <ReactMarkdown remarkPlugins={[remarkGfm]}>{result.explanation}</ReactMarkdown>
            </div>

            <div className="text-[11px] leading-5 text-muted-foreground space-y-0.5">
              <div><span className="text-foreground">ts:</span> {formatTs(result.ts)}</div>
              {result.oid   && <div><span className="text-foreground">oid:</span> {result.oid}</div>}
              {result.sid   && <div><span className="text-foreground">sid:</span> {result.sid}</div>}
              {result.fmt   && <div><span className="text-foreground">fmt:</span> {result.fmt}</div>}
              {result.style && <div><span className="text-foreground">style:</span> {result.style}</div>}
              {metaWithoutWidth && Object.keys(metaWithoutWidth).length > 0 && (
                <div className="font-mono break-all">
                  <span className="font-sans text-foreground">meta:</span>{' '}
                  {JSON.stringify(metaWithoutWidth)}
                </div>
              )}
            </div>
          </>
        ) : (
          <p className="text-sm text-muted-foreground text-center mt-16">No explanation yet</p>
        )}
      </div>
    </div>
  );
}
