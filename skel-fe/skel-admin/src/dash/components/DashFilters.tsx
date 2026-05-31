import React from 'react';
import { IconRefresh, IconTrash, IconPlus } from '../../components/Icons';

export interface DashFilterState {
  search: string;
}

const TIMEZONES: { value: string; label: string }[] = [
  { value: 'local',                label: 'local' },
  { value: 'UTC',                  label: 'GMT' },
  { value: 'Europe/Berlin',        label: 'CET' },
  { value: 'America/New_York',     label: 'ET' },
  { value: 'America/Chicago',      label: 'CT' },
  { value: 'America/Denver',       label: 'MT' },
  { value: 'America/Los_Angeles',  label: 'PT' },
  { value: 'Asia/Hong_Kong',       label: 'HKT' },
];

function tzOffsetLabel(tz: string): string {
  if (tz === 'local') return '';
  try {
    const name = new Intl.DateTimeFormat('en-US', { timeZone: tz, timeZoneName: 'shortOffset' })
      .formatToParts(new Date())
      .find(p => p.type === 'timeZoneName')?.value ?? '';
    return name === 'GMT' ? '+0' : name.replace('GMT', '');
  } catch {
    return '';
  }
}

interface DashFiltersProps {
  filters: DashFilterState;
  timezone: string;
  selectedCount: number;
  hasSelection: boolean;
  onFilterChange: (filters: DashFilterState) => void;
  onTimezoneChange: (tz: string) => void;
  onAdd: () => void;
  onDeleteSelected: () => void;
  onRefresh: () => void;
}

export function DashFilters({
  filters,
  timezone,
  selectedCount,
  hasSelection,
  onFilterChange,
  onTimezoneChange,
  onAdd,
  onDeleteSelected,
  onRefresh,
}: DashFiltersProps) {
  const showDeleteSelected = selectedCount > 0;
  const showAdd = !showDeleteSelected && !hasSelection;

  return (
    <div className="flex flex-wrap items-center gap-3 p-3 bg-card border-b border-border">
      <div className="flex items-center gap-1">
        <label className="text-sm text-muted-foreground whitespace-nowrap">search:</label>
        <input
          type="text"
          value={filters.search}
          onChange={(e) => onFilterChange({ search: e.target.value })}
          placeholder="name, desc, tags..."
          className="text-sm border border-input rounded px-2 py-1 w-48 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
        />
      </div>

      <select
        value={timezone}
        onChange={(e) => onTimezoneChange(e.target.value)}
        className="text-xs border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 cursor-pointer"
      >
        {TIMEZONES.map(({ value, label }) => {
          const off = tzOffsetLabel(value);
          return (
            <option key={value} value={value}>
              {off ? `${label} (${off})` : label}
            </option>
          );
        })}
      </select>

      <div className="flex-1" />

      <button
        onClick={onRefresh}
        className="inline-flex items-center gap-1.5 text-xs bg-muted hover:bg-muted-hover text-foreground px-3 py-1 rounded border border-border transition-colors"
        title="Refresh list"
      >
        <IconRefresh size={14} /> Refresh
      </button>

      {showDeleteSelected && (
        <button
          onClick={onDeleteSelected}
          className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 transition-colors"
        >
          <IconTrash size={13} /> Delete Selected ({selectedCount})
        </button>
      )}
      {showAdd && (
        <button
          onClick={onAdd}
          className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 transition-colors"
        >
          <IconPlus size={13} /> Add
        </button>
      )}
    </div>
  );
}
