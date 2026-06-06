/** Custom explain tag: `!PDF[h=400,w=100](https://example.com/doc.pdf)` */

export const EXPLAIN_PDF_TAG_RE = /!PDF(?:\[([^\]]*)\])?\(([^)]+)\)/g;

export type PdfEmbedOpts = {
  height?: string;
  width?: string;
};

export type ExplainMarkdownSegment =
  | { kind: 'markdown'; content: string }
  | { kind: 'pdf'; url: string; opts: PdfEmbedOpts };

/** Bare numbers: `h` → px, `w` → %. Values with units are used as-is. */
export function parsePdfOpts(optsStr?: string): PdfEmbedOpts {
  if (!optsStr?.trim()) return {};
  const out: PdfEmbedOpts = {};
  for (const part of optsStr.split(',')) {
    const m = part.trim().match(/^([hw])=(.+)$/i);
    if (!m) continue;
    const key = m[1].toLowerCase();
    const css = toCssDimension(key as 'h' | 'w', m[2].trim());
    if (key === 'h') out.height = css;
    if (key === 'w') out.width = css;
  }
  return out;
}

function toCssDimension(axis: 'h' | 'w', raw: string): string {
  if (/^\d+(\.\d+)?(px|%|em|rem|vh|vw)$/i.test(raw)) return raw;
  if (/^\d+(\.\d+)?$/.test(raw)) return axis === 'h' ? `${raw}px` : `${raw}%`;
  return raw;
}

export function isSafePdfUrl(url: string): boolean {
  try {
    const u = new URL(url.trim());
    return u.protocol === 'http:' || u.protocol === 'https:';
  } catch {
    return false;
  }
}

export function splitExplainMarkdown(text: string): ExplainMarkdownSegment[] {
  const segments: ExplainMarkdownSegment[] = [];
  let lastIndex = 0;
  EXPLAIN_PDF_TAG_RE.lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = EXPLAIN_PDF_TAG_RE.exec(text)) !== null) {
    if (match.index > lastIndex) {
      segments.push({ kind: 'markdown', content: text.slice(lastIndex, match.index) });
    }
    const url = match[2].trim();
    if (isSafePdfUrl(url)) {
      segments.push({ kind: 'pdf', url, opts: parsePdfOpts(match[1]) });
    } else {
      segments.push({ kind: 'markdown', content: match[0] });
    }
    lastIndex = match.index + match[0].length;
  }

  if (lastIndex < text.length) {
    segments.push({ kind: 'markdown', content: text.slice(lastIndex) });
  }

  if (segments.length === 0) {
    segments.push({ kind: 'markdown', content: text });
  }

  return segments;
}
