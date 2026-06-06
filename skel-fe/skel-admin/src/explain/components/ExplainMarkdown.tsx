import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { PdfMarkdownEmbed } from './PdfMarkdownEmbed';
import { splitExplainMarkdown } from './explainMarkdownPdf';

export interface ExplainMarkdownProps {
  children: string;
  className?: string;
}

const remarkPlugins = [remarkGfm];

/**
 * Explain markdown renderer: standard GFM via react-markdown plus custom tags.
 *
 * PDF embed: `!PDF[h=400,w=100](https://host/report.pdf)`
 * - `h` — iframe height (bare number → px, e.g. `h=400` → 400px)
 * - `w` — iframe width (bare number → %, e.g. `w=100` → 100%)
 * - URL must be http(s); shown as link above the viewer
 */
export function ExplainMarkdown({ children, className }: ExplainMarkdownProps) {
  const segments = splitExplainMarkdown(children);
  const hasPdf = segments.some((s) => s.kind === 'pdf');

  if (!hasPdf) {
    return (
      <div className={className}>
        <ReactMarkdown remarkPlugins={remarkPlugins}>{children}</ReactMarkdown>
      </div>
    );
  }

  return (
    <div className={className}>
      {segments.map((seg, i) =>
        seg.kind === 'pdf' ? (
          <PdfMarkdownEmbed key={`pdf-${i}`} url={seg.url} opts={seg.opts} />
        ) : seg.content ? (
          <ReactMarkdown key={`md-${i}`} remarkPlugins={remarkPlugins}>
            {seg.content}
          </ReactMarkdown>
        ) : null,
      )}
    </div>
  );
}
