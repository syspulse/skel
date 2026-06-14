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
    <div className="filter-bar">
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
