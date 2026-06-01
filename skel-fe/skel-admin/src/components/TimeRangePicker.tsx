import React, { useState } from 'react';
import type { TimeRange } from '../types';

interface TimeRangePickerProps {
  value: TimeRange;
  onChange: (range: TimeRange) => void;
}

type PresetOption = { label: string; hours: number };

const PRESETS: PresetOption[] = [
  { label: 'Last 1 hour', hours: 1 },
  { label: 'Last 24 hours', hours: 24 },
  { label: 'Last 7 days', hours: 168 },
  { label: 'Last 30 days', hours: 720 },
];

function toDatetimeLocal(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}`
  );
}

export function TimeRangePicker({ value, onChange }: TimeRangePickerProps) {
  const [showCustom, setShowCustom] = useState(
    value.type === 'custom',
  );

  const selectValue =
    value.type === 'all'
      ? 'all'
      : value.type === 'last'
        ? String(value.hours)
        : 'custom';

  const handleSelectChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const v = e.target.value;
    if (v === 'all') {
      setShowCustom(false);
      onChange({ type: 'all' });
    } else if (v === 'custom') {
      setShowCustom(true);
      const now = new Date();
      const start = new Date(now.getTime() - 24 * 60 * 60 * 1000);
      onChange({ type: 'custom', start, end: now });
    } else {
      setShowCustom(false);
      onChange({ type: 'last', hours: Number(v) });
    }
  };

  const handleStartChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (value.type !== 'custom') return;
    onChange({ ...value, start: new Date(e.target.value) });
  };

  const handleEndChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (value.type !== 'custom') return;
    onChange({ ...value, end: new Date(e.target.value) });
  };

  return (
    <div className="flex items-center gap-2 flex-wrap">
      <label className="text-sm text-muted-foreground whitespace-nowrap">
        Time:
      </label>
      <select
        value={selectValue}
        onChange={handleSelectChange}
        className="text-sm border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
      >
        <option value="all">All time</option>
        {PRESETS.map((p) => (
          <option key={p.hours} value={String(p.hours)}>
            {p.label}
          </option>
        ))}
        <option value="custom">Custom range</option>
      </select>

      {showCustom && value.type === 'custom' && (
        <>
          <input
            type="datetime-local"
            value={toDatetimeLocal(value.start)}
            onChange={handleStartChange}
            className="text-sm border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
          />
          <span className="text-sm text-muted-foreground">—</span>
          <input
            type="datetime-local"
            value={toDatetimeLocal(value.end)}
            onChange={handleEndChange}
            className="text-sm border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
          />
        </>
      )}
    </div>
  );
}
