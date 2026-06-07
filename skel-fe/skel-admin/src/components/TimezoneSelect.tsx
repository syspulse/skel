import React from 'react';
import { TIMEZONES, formatTimezoneOption, type TimezoneOption } from './timezone';

const DEFAULT_CLASS =
  'text-xs border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 cursor-pointer';

interface TimezoneSelectProps {
  value: string;
  onChange: (timeZone: string) => void;
  className?: string;
  timezones?: TimezoneOption[];
}

export function TimezoneSelect({
  value,
  onChange,
  className = DEFAULT_CLASS,
  timezones = TIMEZONES,
}: TimezoneSelectProps) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      className={className}
    >
      {timezones.map(({ value: tz, label }) => (
        <option key={tz} value={tz}>
          {formatTimezoneOption(tz, label)}
        </option>
      ))}
    </select>
  );
}
