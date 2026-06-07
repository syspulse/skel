export interface TimezoneOption {
  value: string;
  label: string;
}

export const DEFAULT_TIMEZONE = 'local';

export const TIMEZONES: TimezoneOption[] = [
  { value: 'local',               label: 'local' },
  { value: 'UTC',                 label: 'GMT' },
  { value: 'Europe/Berlin',       label: 'CET' },
  { value: 'America/New_York',    label: 'ET' },
  { value: 'America/Chicago',     label: 'CT' },
  { value: 'America/Denver',      label: 'MT' },
  { value: 'America/Los_Angeles', label: 'PT' },
  { value: 'Asia/Hong_Kong',      label: 'HKT' },
];

const TIMEZONE_LABELS = Object.fromEntries(
  TIMEZONES.map(({ value, label }) => [value, label]),
) as Record<string, string>;

function resolveTimeZone(timeZone: string): string {
  return timeZone === 'local'
    ? Intl.DateTimeFormat().resolvedOptions().timeZone
    : timeZone;
}

/** UTC offset hours as signed 2-digit string, e.g. +02, -05, +00 */
export function tzOffsetHours(timeZone: string): string {
  try {
    const zone = resolveTimeZone(timeZone);
    const name = new Intl.DateTimeFormat('en-US', {
      timeZone: zone,
      timeZoneName: 'shortOffset',
    })
      .formatToParts(new Date())
      .find((p) => p.type === 'timeZoneName')?.value ?? 'GMT';

    if (name === 'GMT') return '+00';

    const match = name.match(/GMT([+-])(\d{1,2})/);
    if (!match) return '+00';

    const sign = match[1];
    const hours = parseInt(match[2], 10);
    return `${sign}${String(hours).padStart(2, '0')}`;
  } catch {
    return '+00';
  }
}

/** Select label: `{HH} {label} ({tz})` */
export function formatTimezoneOption(value: string, label: string): string {
  const hh = tzOffsetHours(value);
  const tz = value === 'local' ? 'local' : value;
  return `${hh} ${label} (${tz})`;
}

export function getTimezoneShortLabel(timeZone: string): string {
  return TIMEZONE_LABELS[timeZone] ?? timeZone;
}
