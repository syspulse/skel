import React, { useCallback, useEffect, useMemo, useState } from 'react';
import * as api from '../api';
import { useAuth } from '../auth/useAuth';
import { ExplainFilters, FilterState } from '../components/ExplainFilters';
import { ExplainSlider } from '../components/ExplainSlider';
import { ExplainTable } from '../components/ExplainTable';
import type {
  Explain,
  ExplainCreateReq,
  ExplainUpdateReq,
  TimeRange,
} from '../types';

function rowKey(rule: Explain): string {
  return `${rule.oid ?? ''}_${rule.rid}`;
}

function isInTimeRange(ts0: number, range: TimeRange): boolean {
  const now = Date.now();
  if (range.type === 'last') {
    return ts0 >= now - range.hours * 60 * 60 * 1000;
  }
  return ts0 >= range.start.getTime() && ts0 <= range.end.getTime();
}

export function ExplainPage() {
  const { token } = useAuth();
  const [rules, setRules] = useState<Explain[]>([]);
  const [loading, setLoading] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [selected, setSelected] = useState<Explain | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [sliderOpen, setSliderOpen] = useState(false);
  const [addMode, setAddMode] = useState(false);
  const [utc, setUtc] = useState(false);

  const [filters, setFilters] = useState<FilterState>({
    oid: '',
    rid: '',
    timeRange: { type: 'last', hours: 24 } as TimeRange,
  });

  const fetchRules = useCallback(async () => {
    setLoading(true);
    setFetchError(null);
    try {
      const result = await api.listRules(token);
      // Sort by ts0 desc
      const sorted = [...(result.data ?? [])].sort((a, b) => b.ts0 - a.ts0);
      setRules(sorted);
    } catch (e) {
      setFetchError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => {
    fetchRules();
  }, [fetchRules]);

  // Frontend filtering
  const filteredRules = useMemo(() => {
    return rules.filter((rule) => {
      if (
        filters.oid &&
        !(rule.oid ?? '').toLowerCase().includes(filters.oid.toLowerCase())
      ) {
        return false;
      }
      if (
        filters.rid &&
        !rule.rid.toLowerCase().includes(filters.rid.toLowerCase())
      ) {
        return false;
      }
      if (!isInTimeRange(rule.ts0, filters.timeRange)) {
        return false;
      }
      return true;
    });
  }, [rules, filters]);

  const handleRowClick = (rule: Explain) => {
    setSelected(rule);
    setAddMode(false);
    setSliderOpen(true);
  };

  const handleCheckboxChange = (rule: Explain, checked: boolean) => {
    const key = rowKey(rule);
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(key);
      else next.delete(key);
      return next;
    });
  };

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedIds(new Set(filteredRules.map(rowKey)));
    } else {
      setSelectedIds(new Set());
    }
  };

  const handleAdd = () => {
    setSelected(null);
    setAddMode(true);
    setSliderOpen(true);
  };

  const handleCloseSlider = () => {
    setSliderOpen(false);
    setSelected(null);
    setAddMode(false);
  };

  const handleCreate = async (rid: string, req: ExplainCreateReq) => {
    await api.createRule(token, rid, req);
    setSliderOpen(false);
    setSelected(null);
    await fetchRules();
  };

  const handleUpdate = async (rid: string, req: ExplainUpdateReq) => {
    await api.updateRule(token, rid, req);
    setSliderOpen(false);
    setSelected(null);
    await fetchRules();
  };

  const handleDelete = async (rule: Explain) => {
    if (!window.confirm(`Delete rule "${rule.rid}"?`)) return;
    await api.deleteRule(token, rule.rid, rule.oid);
    setSliderOpen(false);
    setSelected(null);
    await fetchRules();
  };

  const handleDeleteSelected = async () => {
    if (selectedIds.size === 0) return;
    if (
      !window.confirm(
        `Delete ${selectedIds.size} selected rule(s)? This cannot be undone.`,
      )
    )
      return;

    // Find the actual rules matching selected keys
    const toDelete = filteredRules.filter((r) => selectedIds.has(rowKey(r)));
    for (const rule of toDelete) {
      try {
        await api.deleteRule(token, rule.rid, rule.oid);
      } catch {
        // continue deleting others
      }
    }
    setSelectedIds(new Set());
    setSelected(null);
    setSliderOpen(false);
    await fetchRules();
  };

  return (
    <div className="flex flex-col h-full relative">
      {/* Filters */}
      <ExplainFilters
        filters={filters}
        utc={utc}
        selectedCount={selectedIds.size}
        hasSelection={selected !== null}
        onFilterChange={setFilters}
        onUtcToggle={() => setUtc((u) => !u)}
        onAdd={handleAdd}
        onDeleteSelected={handleDeleteSelected}
        onRefresh={fetchRules}
      />

      {/* Status bar */}
      <div className="px-4 py-1 text-xs text-gray-500 bg-gray-50 border-b border-gray-200 flex items-center gap-3">
        {loading && <span className="text-blue-500">Loading…</span>}
        {!loading && (
          <span>
            {filteredRules.length} rule{filteredRules.length !== 1 ? 's' : ''}
            {filteredRules.length !== rules.length && ` (filtered from ${rules.length})`}
          </span>
        )}
        {fetchError && (
          <span className="text-red-500 flex items-center gap-1">
            ⚠ {fetchError}
          </span>
        )}
      </div>

      {/* Table */}
      <div className="flex-1 overflow-auto bg-white">
        {loading && rules.length === 0 ? (
          <div className="flex items-center justify-center py-20 text-gray-400 text-sm">
            Loading rules…
          </div>
        ) : (
          <ExplainTable
            rules={filteredRules}
            selected={selected}
            selectedIds={selectedIds}
            utc={utc}
            onRowClick={handleRowClick}
            onCheckboxChange={handleCheckboxChange}
            onSelectAll={handleSelectAll}
          />
        )}
      </div>

      {/* Slider */}
      <ExplainSlider
        open={sliderOpen}
        addMode={addMode}
        rule={selected}
        onClose={handleCloseSlider}
        onCreate={handleCreate}
        onUpdate={handleUpdate}
        onDelete={handleDelete}
      />
    </div>
  );
}
