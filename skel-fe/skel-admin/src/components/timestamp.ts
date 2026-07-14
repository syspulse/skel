// Timestamp rendering uses moment.js (moment-timezone). The format is a free-text moment pattern
// (e.g. "MMMM Do YYYY, h:mm:ss a") configured in Settings — the layout is fully user-controlled and
// the runtime/system locale never overrides it.
import moment from 'moment-timezone';

export const DEFAULT_TIMESTAMP_FORMAT = 'HH:mm:ss D-MMM-YYYY';
export const US_TIMESTAMP_FORMAT = 'MMMM Do YYYY, h:mm:ss a';
export const ISO_TIMESTAMP_FORMAT = 'YYYY-MM-DDTHH:mm:ss';
export const MS_TIMESTAMP_FORMAT = 'x'; // moment token: Unix ms timestamp

export interface TimestampFormatPreset {
  pattern: string;
  labelKey: string;
}

export const TIMESTAMP_FORMAT_PRESETS: readonly TimestampFormatPreset[] = [
  { pattern: DEFAULT_TIMESTAMP_FORMAT, labelKey: 'settings.timestampFormat.default' },
  { pattern: US_TIMESTAMP_FORMAT, labelKey: 'settings.timestampFormat.us' },
  { pattern: ISO_TIMESTAMP_FORMAT, labelKey: 'settings.timestampFormat.iso' },
  { pattern: MS_TIMESTAMP_FORMAT, labelKey: 'settings.timestampFormat.ms' },
];

// Legacy stored values from earlier (non-moment) implementations -> equivalent moment pattern.
const LEGACY_PATTERNS: Record<string, string> = {
  datetime: DEFAULT_TIMESTAMP_FORMAT,
  us: US_TIMESTAMP_FORMAT,
  iso: ISO_TIMESTAMP_FORMAT,
  ms: MS_TIMESTAMP_FORMAT,
  S: MS_TIMESTAMP_FORMAT,
};

export function normalizeTimestampPattern(pattern: string): string {
  const trimmed = (pattern ?? '').trim();
  if (!trimmed) return DEFAULT_TIMESTAMP_FORMAT;
  return LEGACY_PATTERNS[trimmed] ?? trimmed;
}

export function isKnownTimestampPreset(pattern: string): boolean {
  const normalized = normalizeTimestampPattern(pattern);
  return TIMESTAMP_FORMAT_PRESETS.some((preset) => preset.pattern === normalized);
}

export function formatTimestamp(ts: number, timezone: string, pattern: string): string {
  const fmt = normalizeTimestampPattern(pattern);
  const m = timezone === 'local' ? moment(ts) : moment(ts).tz(timezone);
  return m.format(fmt);
}
