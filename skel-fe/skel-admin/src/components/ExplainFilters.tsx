import React from 'react';
import type { TimeRange } from '../types';
import { TimeRangePicker } from './TimeRangePicker';
import { IconRefresh, IconTrash, IconPlus } from './Icons';

export interface FilterState {
  oid: string;
  rid: string;
  timeRange: TimeRange;
}

interface ExplainFiltersProps {
  filters: FilterState;
  utc: boolean;
  selectedCount: number;
  hasSelection: boolean; // single selected rule
  onFilterChange: (filters: FilterState) => void;
  onUtcToggle: () => void;
  onAdd: () => void;
  onDeleteSelected: () => void;
  onRefresh: () => void;
}

export function ExplainFilters({
  filters,
  utc,
  selectedCount,
  hasSelection,
  onFilterChange,
  onUtcToggle,
  onAdd,
  onDeleteSelected,
  onRefresh,
}: ExplainFiltersProps) {
  const showDeleteSelected = selectedCount > 0;
  const showAdd = !showDeleteSelected && !hasSelection;

  return (
    <div className="flex flex-wrap items-center gap-3 p-3 bg-white border-b border-gray-200">
      {/* OID filter */}
      <div className="flex items-center gap-1">
        <label className="text-sm text-gray-600 font-medium whitespace-nowrap">
          OID:
        </label>
        <input
          type="text"
          value={filters.oid}
          onChange={(e) => onFilterChange({ ...filters, oid: e.target.value })}
          placeholder="filter..."
          className="text-sm border border-gray-300 rounded px-2 py-1 w-36 focus:outline-none focus:ring-1 focus:ring-blue-400"
        />
      </div>

      {/* RID filter */}
      <div className="flex items-center gap-1">
        <label className="text-sm text-gray-600 font-medium whitespace-nowrap">
          RID:
        </label>
        <input
          type="text"
          value={filters.rid}
          onChange={(e) => onFilterChange({ ...filters, rid: e.target.value })}
          placeholder="filter..."
          className="text-sm border border-gray-300 rounded px-2 py-1 w-36 focus:outline-none focus:ring-1 focus:ring-blue-400"
        />
      </div>

      {/* Time range picker + GMT checkbox */}
      <TimeRangePicker
        value={filters.timeRange}
        onChange={(timeRange) => onFilterChange({ ...filters, timeRange })}
      />
      <label className="flex items-center gap-1.5 text-sm text-gray-600 font-medium cursor-pointer select-none whitespace-nowrap">
        <input
          type="checkbox"
          checked={utc}
          onChange={onUtcToggle}
          className="w-3.5 h-3.5 cursor-pointer accent-blue-600"
        />
        GMT
      </label>

      {/* Spacer */}
      <div className="flex-1" />

      {/* Refresh */}
      <button
        onClick={onRefresh}
        className="inline-flex items-center gap-1.5 text-xs bg-gray-100 hover:bg-gray-200 text-gray-700 px-3 py-1 rounded border border-gray-300 transition-colors"
        title="Refresh list"
      >
        <IconRefresh size={14} /> Refresh
      </button>

      {/* Add / Delete Selected */}
      {showDeleteSelected && (
        <button
          onClick={onDeleteSelected}
          className="inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 transition-colors"
        >
          <IconTrash size={13} /> Delete Selected ({selectedCount})
        </button>
      )}
      {showAdd && (
        <button
          onClick={onAdd}
          className="inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 transition-colors"
        >
          <IconPlus size={13} /> Add
        </button>
      )}
    </div>
  );
}
