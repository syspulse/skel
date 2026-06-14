import React from 'react';
import { useTranslation } from 'react-i18next';
import type { TimeRange } from '../../../types';
import { TimeRangePicker } from '../../../components/TimeRangePicker';
import { TimezoneSelect } from '../../../components/TimezoneSelect';
import { IconRefresh, IconTrash, IconPlus } from '../../../components/Icons';
import { SearchField } from '../../../components/SearchField';
import { FilterText } from '../../../components/FilterField';

export interface FilterState {
  search: string;
  oid: string;
  rid: string;
  timeRange: TimeRange;
}

const SEARCH_PRESETS = [
  '',
  '.*', 
  'Detector',
  'Aml',
  'Wallet',
  'let'
];

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
    <div className="filter-bar">
      <SearchField
        value={filters.search}
        onChange={(val) => onFilterChange({ ...filters, search: val })}
        onSearch={onSearch}
        presets={SEARCH_PRESETS}
        placeholder={t('explain.searchPlaceholder')}
        className="w-44"
      />

      <FilterText
        label={t('explain.fields.oid')}
        value={filters.oid}
        onChange={(oid) => onFilterChange({ ...filters, oid })}
        placeholder={t('common.filterPlaceholder')}
      />

      <FilterText
        label={t('explain.fields.rid')}
        value={filters.rid}
        onChange={(rid) => onFilterChange({ ...filters, rid })}
        placeholder={t('common.filterPlaceholder')}
      />

      <TimeRangePicker
        value={filters.timeRange}
        onChange={(timeRange) => onFilterChange({ ...filters, timeRange })}
      />
      <TimezoneSelect value={timezone} onChange={onTimezoneChange} />

      <div className="flex-1" />

      <button
        onClick={onRefresh}
        className="btn-toolbar"
      >
        <IconRefresh size={14} /> {t('common.refresh')}
      </button>

      {showDeleteSelected && (
        <button
          onClick={onDeleteSelected}
          className="btn-danger"
        >
          <IconTrash size={13} /> {t('common.deleteSelected', { count: selectedCount })}
        </button>
      )}
      {showAdd && (
        <button
          onClick={onAdd}
          className="btn-add"
        >
          <IconPlus size={13} /> {t('common.add')}
        </button>
      )}
    </div>
  );
}
