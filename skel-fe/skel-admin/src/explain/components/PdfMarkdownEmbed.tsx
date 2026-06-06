import React from 'react';
import type { PdfEmbedOpts } from './explainMarkdownPdf';

const DEFAULT_HEIGHT = '400px';
const DEFAULT_WIDTH = '100%';

interface PdfMarkdownEmbedProps {
  url: string;
  opts?: PdfEmbedOpts;
}

export function PdfMarkdownEmbed({ url, opts = {} }: PdfMarkdownEmbedProps) {
  const height = opts.height ?? DEFAULT_HEIGHT;
  const width = opts.width ?? DEFAULT_WIDTH;

  return (
    <figure className="my-3 not-prose border border-border rounded overflow-hidden bg-card">
      <figcaption className="px-3 py-2 border-b border-border bg-muted text-xs">
        <a
          href={url}
          target="_blank"
          rel="noopener noreferrer"
          className="text-primary hover:underline break-all"
        >
          {url}
        </a>
      </figcaption>
      <iframe
        src={url}
        title="PDF document"
        style={{ height, width, border: 0, display: 'block', maxWidth: '100%' }}
      />
    </figure>
  );
}
