import React from 'react';
import { TIMEZONES, formatTimezoneOption, type TimezoneOption } from './timezone';

const DEFAULT_CLASS =
  'text-xs field px-2 py-1 bg-card cursor-pointer';

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
