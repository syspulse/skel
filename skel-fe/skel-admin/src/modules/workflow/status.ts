// Engine runtime status colors (matching the Temporal UI): COMPLETED=green, RUNNING=blue,
// FAILED=red, TERMINATED=yellow, CANCELED=white. Used for the status labels on the
// WorkflowConfig view / editor panel and on the graph nodes (DetectorConfig status).
import type { CSSProperties } from 'react';

export interface StatusStyle {
  bg: string;
  fg: string;
  border?: string;
}

// Colors picked to match the Temporal UI status chips (pastel background, dark text).
const DARK = '#0f172a';
export function statusStyle(status?: string): StatusStyle {
  switch ((status ?? '').toUpperCase()) {
    case 'COMPLETED':          return { bg: '#86efac', fg: DARK };  // green
    case 'RUNNING':            return { bg: '#93c5fd', fg: DARK };  // blue
    case 'WAITING':            return { bg: '#bae6fd', fg: DARK };  // light blue
    case 'FAILED':             return { bg: '#f8a488', fg: DARK };  // salmon/orange
    case 'TERMINATED':         return { bg: '#e5d54a', fg: DARK };  // yellow
    case 'CANCELED':
    case 'CANCELLED':          return { bg: '#9ca3af', fg: DARK };  // gray
    case 'TIMED_OUT':          return { bg: '#fdba74', fg: DARK };  // orange
    case 'CONTINUED_AS_NEW':   return { bg: '#d8b4fe', fg: DARK };  // purple
    case 'ACTIVE':             return { bg: '#86efac', fg: DARK };
    case 'DISABLED':           return { bg: '#fde68a', fg: DARK };
    case 'DELETED':            return { bg: '#fca5a5', fg: DARK };
    default:                   return { bg: '#e5e7eb', fg: '#374151' };  // gray (UNKNOWN/…)
  }
}

/** Inline style for a small status chip. */
export function statusChipStyle(status?: string): CSSProperties {
  const s = statusStyle(status);
  return { background: s.bg, color: s.fg, border: s.border };
}
