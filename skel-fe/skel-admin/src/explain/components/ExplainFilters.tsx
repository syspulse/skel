import React from 'react';
import { useTranslation } from 'react-i18next';
import type { TimeRange } from '../../types';
import { TimeRangePicker } from '../../components/TimeRangePicker';
import { IconRefresh, IconTrash, IconPlus } from '../../components/Icons';
import { SearchField } from '../../components/SearchField';

export interface FilterState {
  search: string;
  oid: string;
  rid: string;
  timeRange: TimeRange;
}

const SEARCH_PRESETS = ['.*', 'Explain'];

export const TIMEZONES: { value: string; label: string }[] = [
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

interface ExplainFiltersProps {
  filters: FilterState;
  timezone: string;
  selectedCount: number;
  hasSelection: boolean;
  onFilterChange: (filters: FilterState) => void;
  onTimezoneChange: (tz: string) => void;
  onSearch: (query: string) => void;
  onAdd: () => void;
  onDeleteSelected: () => void;
  onRefresh: () => void;
}

export function ExplainFilters({
  filters,
  timezone,
  selectedCount,
  hasSelection,
  onFilterChange,
  onTimezoneChange,
  onSearch,
  onAdd,
  onDeleteSelected,
  onRefresh,
}: ExplainFiltersProps) {
  const { t } = useTranslation();
  const showDeleteSelected = selectedCount > 0;
  const showAdd = !showDeleteSelected && !hasSelection;

  return (
    <div className="flex flex-wrap items-center gap-3 p-3 bg-card border-b border-border">
      <SearchField
        value={filters.search}
        onChange={(val) => onFilterChange({ ...filters, search: val })}
        onSearch={onSearch}
        presets={SEARCH_PRESETS}
        placeholder={t('explain.searchPlaceholder')}
        className="w-44"
      />

      <div className="flex items-center gap-1">
        <label className="text-sm text-muted-foreground whitespace-nowrap">{t('explain.fields.oid')}:</label>
        <input
          type="text"
          value={filters.oid}
          onChange={(e) => onFilterChange({ ...filters, oid: e.target.value })}
          placeholder={t('common.filterPlaceholder')}
          className="text-sm border border-input rounded px-2 py-1 w-36 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
        />
      </div>

      <div className="flex items-center gap-1">
        <label className="text-sm text-muted-foreground whitespace-nowrap">{t('explain.fields.rid')}:</label>
        <input
          type="text"
          value={filters.rid}
          onChange={(e) => onFilterChange({ ...filters, rid: e.target.value })}
          placeholder={t('common.filterPlaceholder')}
          className="text-sm border border-input rounded px-2 py-1 w-36 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
        />
      </div>

      <TimeRangePicker
        value={filters.timeRange}
        onChange={(timeRange) => onFilterChange({ ...filters, timeRange })}
      />
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
      >
        <IconRefresh size={14} /> {t('common.refresh')}
      </button>

      {showDeleteSelected && (
        <button
          onClick={onDeleteSelected}
          className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 transition-colors"
        >
          <IconTrash size={13} /> {t('common.deleteSelected', { count: selectedCount })}
        </button>
      )}
      {showAdd && (
        <button
          onClick={onAdd}
          className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 transition-colors"
        >
          <IconPlus size={13} /> {t('common.add')}
        </button>
      )}
    </div>
  );
}
