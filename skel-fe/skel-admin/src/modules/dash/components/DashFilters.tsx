import React from 'react';
import { useTranslation } from 'react-i18next';
import { TimezoneSelect } from '../../../components/TimezoneSelect';
import { IconRefresh, IconTrash, IconPlus } from '../../../components/Icons';

export interface DashFilterState {
  search: string;
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
  const { t } = useTranslation();
  const showDeleteSelected = selectedCount > 0;
  const showAdd = !showDeleteSelected && !hasSelection;

  return (
    <div className="flex flex-wrap items-center gap-3 p-3 bg-card border-b border-border">
      <input
        type="text"
        value={filters.search}
        onChange={(e) => onFilterChange({ search: e.target.value })}
        placeholder={t('dash.searchPlaceholder')}
        className="text-sm field px-2 py-1 w-48 bg-card"
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
