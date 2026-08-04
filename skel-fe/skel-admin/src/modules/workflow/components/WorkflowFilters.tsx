import React from 'react';
import { useTranslation } from 'react-i18next';
import type { TimeRange } from '../../../types';
import { TimeRangePicker } from '../../../components/TimeRangePicker';
import { TimezoneSelect } from '../../../components/TimezoneSelect';
import { SearchField } from '../../../components/SearchField';
import { FilterText } from '../../../components/FilterField';
import { IconRefresh, IconPlus } from '../../../components/Icons';

export interface WorkflowFilterState {
  ids: string;           // id filter: single id or CSV list (e.g. "3" or "1,2,5")
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
  onResolve?: () => void;   // WorkflowConfig tab only: resolve all configs (by xid) against the Engine
  resolving?: boolean;
}

// Same UI/UX as ExplainFilters, reusing the same building blocks (SearchField, TimeRangePicker,
// TimezoneSelect). Filters apply to the active Workflow tab.
export function WorkflowFilters({
  filters, timezone, onFilterChange, onTimezoneChange, onSearch, onAdd, onRefresh, onResolve, resolving,
}: WorkflowFiltersProps) {
  const { t } = useTranslation();

  return (
    <div className="filter-bar">
      <FilterText
        label={t('workflow.fields.id')}
        value={filters.ids}
        onChange={(ids) => onFilterChange({ ...filters, ids })}
        placeholder=""
        width="w-28"
      />

      <SearchField
        value={filters.search}
        onChange={(val) => onFilterChange({ ...filters, search: val })}
        onSearch={onSearch}
        presets={SEARCH_PRESETS}
        placeholder={t('workflow.searchPlaceholder')}
        className="w-44"
      />

      <FilterText
        label={t('workflow.fields.status')}
        value={filters.status}
        onChange={(status) => onFilterChange({ ...filters, status })}
        placeholder={t('common.filterPlaceholder')}
      />

      <TimeRangePicker
        value={filters.timeRange}
        onChange={(timeRange) => onFilterChange({ ...filters, timeRange })}
      />
      <TimezoneSelect value={timezone} onChange={onTimezoneChange} />

      <div className="flex-1" />

      {onResolve && (
        <button
          onClick={onResolve}
          disabled={resolving}
          className="btn-toolbar disabled:opacity-40 disabled:cursor-not-allowed"
        >
          <IconRefresh size={14} /> {resolving ? t('workflow.resolving') : t('workflow.resolve')}
        </button>
      )}
      <button
        onClick={onRefresh}
        className="btn-toolbar"
      >
        <IconRefresh size={14} /> {t('common.refresh')}
      </button>
      <button
        onClick={onAdd}
        className="btn-add"
      >
        <IconPlus size={13} /> {t('common.add')}
      </button>
    </div>
  );
}
