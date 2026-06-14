import React from 'react';
import { useTranslation } from 'react-i18next';
import type { TimeRange } from '../../../types';
import { TimeRangePicker } from '../../../components/TimeRangePicker';
import { TimezoneSelect } from '../../../components/TimezoneSelect';
import { SearchField } from '../../../components/SearchField';
import { IconRefresh, IconPlus } from '../../../components/Icons';

export interface WorkflowFilterState {
  search: string;        // name / title search
  status: string;        // status filter (API field)
  timeRange: TimeRange;  // filters by updatedAt (ts)
}

const SEARCH_PRESETS = ['', 'Workflow', 'Detector', 'Schema', 'Config'];

interface WorkflowFiltersProps {
  filters: WorkflowFilterState;
  timezone: string;
  onFilterChange: (filters: WorkflowFilterState) => void;
  onTimezoneChange: (tz: string) => void;
  onSearch: (query: string) => void;
  onAdd: () => void;
  onRefresh: () => void;
}

// Same UI/UX as ExplainFilters, reusing the same building blocks (SearchField, TimeRangePicker,
// TimezoneSelect). Filters apply to the active Workflow tab.
export function WorkflowFilters({
  filters, timezone, onFilterChange, onTimezoneChange, onSearch, onAdd, onRefresh,
}: WorkflowFiltersProps) {
  const { t } = useTranslation();

  return (
    <div className="flex flex-wrap items-center gap-3 p-3 bg-card border-b border-border">
      <SearchField
        value={filters.search}
        onChange={(val) => onFilterChange({ ...filters, search: val })}
        onSearch={onSearch}
        presets={SEARCH_PRESETS}
        placeholder={t('workflow.searchPlaceholder')}
        className="w-44"
      />

      <div className="flex items-center gap-1">
        <label className="text-sm text-muted-foreground whitespace-nowrap">{t('workflow.fields.status')}:</label>
        <input
          type="text"
          value={filters.status}
          onChange={(e) => onFilterChange({ ...filters, status: e.target.value })}
          placeholder={t('common.filterPlaceholder')}
          className="text-sm border border-input rounded px-2 py-1 w-36 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
        />
      </div>

      <TimeRangePicker
        value={filters.timeRange}
        onChange={(timeRange) => onFilterChange({ ...filters, timeRange })}
      />
      <TimezoneSelect value={timezone} onChange={onTimezoneChange} />

      <div className="flex-1" />

      <button
        onClick={onRefresh}
        className="inline-flex items-center gap-1.5 text-xs bg-muted hover:bg-muted-hover text-foreground px-3 py-1 rounded border border-border transition-colors"
      >
        <IconRefresh size={14} /> {t('common.refresh')}
      </button>
      <button
        onClick={onAdd}
        className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 transition-colors"
      >
        <IconPlus size={13} /> {t('common.add')}
      </button>
    </div>
  );
}
