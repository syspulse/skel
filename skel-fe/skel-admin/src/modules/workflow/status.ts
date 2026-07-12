// Engine runtime status colors (matching the Temporal UI): COMPLETED=green, RUNNING=blue,
// FAILED=red, TERMINATED=yellow, CANCELED=white. Used for the status labels on the
// WorkflowConfig view / editor panel and on the graph nodes (DetectorConfig status).
import type { CSSProperties } from 'react';

export interface StatusStyle {
  bg: string;
  fg: string;
  border?: string;
}

export function statusStyle(status?: string): StatusStyle {
  switch ((status ?? '').toUpperCase()) {
    case 'COMPLETED':          return { bg: '#22c55e', fg: '#1f2937' };                          // green
    case 'RUNNING':            return { bg: '#3b82f6', fg: '#ffffff' };                          // blue
    case 'WAITING':            return { bg: '#38bdf8', fg: '#0f172a' };                          // light blue
    case 'FAILED':             return { bg: '#ef4444', fg: '#ffffff' };                          // red
    case 'TERMINATED':         return { bg: '#eab308', fg: '#1f2937' };                          // yellow
    case 'CANCELED':
    case 'CANCELLED':          return { bg: '#ffffff', fg: '#1f2937', border: '1px solid #cbd5e1' }; // white
    case 'TIMED_OUT':          return { bg: '#f97316', fg: '#ffffff' };                          // orange
    case 'CONTINUED_AS_NEW':   return { bg: '#a855f7', fg: '#ffffff' };                          // purple
    case 'ACTIVE':             return { bg: '#d1fae5', fg: '#047857' };                          // emerald (stored)
    case 'DISABLED':           return { bg: '#fef3c7', fg: '#b45309' };
    case 'DELETED':            return { bg: '#fee2e2', fg: '#b91c1c' };
    default:                   return { bg: '#e5e7eb', fg: '#374151' };                          // gray (UNKNOWN/…)
  }
}

/** Inline style for a small status chip. */
export function statusChipStyle(status?: string): CSSProperties {
  const s = statusStyle(status);
  return { background: s.bg, color: s.fg, border: s.border };
}
