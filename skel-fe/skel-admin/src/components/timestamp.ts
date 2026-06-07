const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export const DEFAULT_TIMESTAMP_FORMAT = 'D MMM HH:mm:ss';
export const US_TIMESTAMP_FORMAT = 'MMM D, YYYY, h:mm:ss A';
export const MS_TIMESTAMP_FORMAT = 'S';

const LEGACY_PATTERNS: Record<string, string> = {
  us: US_TIMESTAMP_FORMAT,
  ms: MS_TIMESTAMP_FORMAT,
};

export interface TimestampFormatPreset {
  pattern: string;
  labelKey: string;
}

export const TIMESTAMP_FORMAT_PRESETS: readonly TimestampFormatPreset[] = [
  { pattern: DEFAULT_TIMESTAMP_FORMAT, labelKey: 'settings.timestampFormat.default' },
  { pattern: US_TIMESTAMP_FORMAT, labelKey: 'settings.timestampFormat.us' },
  { pattern: MS_TIMESTAMP_FORMAT, labelKey: 'settings.timestampFormat.ms' },
];

interface DateParts {
  day: string;
  month: string;
  year: string;
  hour24: string;
  hour12: string;
  minute: string;
  second: string;
  ampm: string;
}

function getDateParts(d: Date, timezone: string): DateParts {
  if (timezone === 'local') {
    const hours = d.getHours();
    return {
      day: String(d.getDate()),
      month: MONTHS[d.getMonth()],
      year: String(d.getFullYear()),
      hour24: String(hours).padStart(2, '0'),
      hour12: String(hours % 12 || 12),
      minute: String(d.getMinutes()).padStart(2, '0'),
      second: String(d.getSeconds()).padStart(2, '0'),
      ampm: hours >= 12 ? 'PM' : 'AM',
    };
  }

  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: timezone,
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    second: '2-digit',
    hour12: true,
  }).formatToParts(d);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';

  const hour24 = new Intl.DateTimeFormat('en-GB', {
    timeZone: timezone,
    hour: '2-digit',
    hour12: false,
  })
    .formatToParts(d)
    .find((p) => p.type === 'hour')?.value ?? '00';

  const dayPeriod = get('dayPeriod');
  const ampm = dayPeriod ? dayPeriod.toUpperCase() : parseInt(hour24, 10) >= 12 ? 'PM' : 'AM';

  return {
    day: get('day'),
    month: get('month'),
    year: get('year'),
    hour24,
    hour12: get('hour'),
    minute: get('minute'),
    second: get('second'),
    ampm,
  };
}

function formatTokenPattern(ts: number, timezone: string, pattern: string): string {
  const parts = getDateParts(new Date(ts), timezone);
  const epoch = String(ts);

  return pattern
    .replace(/YYYY/g, parts.year)
    .replace(/HH/g, parts.hour24)
    .replace(/MMM/g, parts.month)
    .replace(/mm/g, parts.minute)
    .replace(/ss/g, parts.second)
    .replace(/A/g, parts.ampm)
    .replace(/S/g, epoch)
    .replace(/D/g, parts.day)
    .replace(/h/g, parts.hour12);
}

export function normalizeTimestampPattern(pattern: string): string {
  const trimmed = pattern.trim() || DEFAULT_TIMESTAMP_FORMAT;
  return LEGACY_PATTERNS[trimmed] ?? trimmed;
}

export function formatTimestamp(ts: number, timezone: string, pattern: string): string {
  return formatTokenPattern(ts, timezone, normalizeTimestampPattern(pattern));
}

export function isKnownTimestampPreset(pattern: string): boolean {
  const normalized = normalizeTimestampPattern(pattern);
  return TIMESTAMP_FORMAT_PRESETS.some((preset) => preset.pattern === normalized);
}
