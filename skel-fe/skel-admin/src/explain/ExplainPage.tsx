import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from './api';
import { useAuth } from '../auth/useAuth';
import { useNotifications } from '../notifications/NotificationContext';
import { usePageSize } from '../settings/PageSizeContext';
import { Pagination } from '../components/Pagination';
import { ExplainFilters, FilterState } from './components/ExplainFilters';
import { ExplainSlider } from './components/ExplainSlider';
import { ExplainTable } from './components/ExplainTable';
import type { Explain, ExplainCreateReq, ExplainUpdateReq } from './types';
import type { TimeRange } from '../types';

function rowKey(rule: Explain): string {
  return `${rule.oid ?? ''}_${rule.rid}`;
}

function isInTimeRange(ts0: number, range: TimeRange): boolean {
  if (range.type === 'all') return true;
  const now = Date.now();
  if (range.type === 'last') {
    return ts0 >= now - range.hours * 60 * 60 * 1000;
  }
  return ts0 >= range.start.getTime() && ts0 <= range.end.getTime();
}

function totalPagesFor(count: number, pageSize: number): number {
  return Math.max(1, Math.ceil(count / pageSize) || 1);
}

export function ExplainPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const { add: notify } = useNotifications();
  const { pageSize, setPageSize } = usePageSize();
  const [page, setPage] = useState(1);
  const [rules, setRules] = useState<Explain[]>([]);
  const [loading, setLoading] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [selected, setSelected] = useState<Explain | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [sliderOpen, setSliderOpen] = useState(false);
  const [addMode, setAddMode] = useState(false);
  const [timezone, setTimezone] = useState('local');

  const [activeSearch, setActiveSearch] = useState('');

  const [filters, setFilters] = useState<FilterState>({
    search: '',
    oid: '',
    rid: '',
    // Rules are config records; default to all time so the list is not empty after load.
    timeRange: { type: 'all' } as TimeRange,
  });

  const fetchRules = useCallback(async () => {
    setLoading(true);
    setFetchError(null);
    try {
      let result;
      if (activeSearch.trim()) {
        result = await api.searchRules(token, activeSearch.trim(), 0, 10);
      } else {
        result = await api.listRules(token);
      }
      const sorted = [...(result.data ?? [])].sort((a, b) => b.ts0 - a.ts0);
      setRules(sorted);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setFetchError(msg);
      notify('error', t('explain.errorLoad'), msg);
    } finally {
      setLoading(false);
    }
  }, [token, notify, t, activeSearch]);

  useEffect(() => { fetchRules(); }, [fetchRules]);

  const filteredRules = useMemo(() => {
    if (activeSearch.trim()) return rules;
    return rules.filter((rule) => {
      if (filters.oid && !(rule.oid ?? '').toLowerCase().includes(filters.oid.toLowerCase())) return false;
      if (filters.rid && !rule.rid.toLowerCase().includes(filters.rid.toLowerCase())) return false;
      if (!isInTimeRange(rule.ts0, filters.timeRange)) return false;
      return true;
    });
  }, [rules, filters, activeSearch]);

  const totalPages = totalPagesFor(filteredRules.length, pageSize);
  const safePage = Math.min(Math.max(1, page), totalPages);

  const pagedRules = useMemo(
    () => filteredRules.slice((safePage - 1) * pageSize, safePage * pageSize),
    [filteredRules, safePage, pageSize],
  );

  // Reset to page 1 when filters or page size change
  useEffect(() => { setPage(1); }, [filters, pageSize]);

  // Clamp page when result count shrinks (e.g. delete, tighter filter) — avoids empty table on page 2+
  useEffect(() => {
    setPage((p) => Math.min(Math.max(1, p), totalPagesFor(filteredRules.length, pageSize)));
  }, [filteredRules.length, pageSize]);

  const handleRowClick = (rule: Explain) => {
    setSelected(rule); setAddMode(false); setSliderOpen(true);
  };

  const handleCheckboxChange = (rule: Explain, checked: boolean) => {
    const key = rowKey(rule);
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(key); else next.delete(key);
      return next;
    });
  };

  const handleSelectAll = (checked: boolean) => {
    setSelectedIds(checked ? new Set(filteredRules.map(rowKey)) : new Set());
  };

  const handleSearch = (query: string) => { setActiveSearch(query); };

  const handleAdd = () => { setSelected(null); setAddMode(true); setSliderOpen(true); };

  const handleCloseSlider = () => { setSliderOpen(false); setSelected(null); setAddMode(false); };

  const handleCreate = async (rid: string, req: ExplainCreateReq) => {
    await api.createRule(token, rid, req);
    setSliderOpen(false); setSelected(null); await fetchRules();
  };

  const handleUpdate = async (rid: string, req: ExplainUpdateReq) => {
    await api.updateRule(token, rid, req);
    setSliderOpen(false); setSelected(null); await fetchRules();
  };

  const handleDelete = async (rule: Explain) => {
    if (!window.confirm(t('explain.confirmDelete', { rid: rule.rid }))) return;
    await api.deleteRule(token, rule.rid, rule.oid);
    setSliderOpen(false); setSelected(null); await fetchRules();
  };

  const handleDeleteSelected = async () => {
    if (selectedIds.size === 0) return;
    if (!window.confirm(t('explain.confirmDeleteSelected', { count: selectedIds.size }))) return;
    const toDelete = filteredRules.filter((r) => selectedIds.has(rowKey(r)));
    for (const rule of toDelete) {
      try { await api.deleteRule(token, rule.rid, rule.oid); } catch { /* continue */ }
    }
    setSelectedIds(new Set()); setSelected(null); setSliderOpen(false);
    await fetchRules();
  };

  const countLabel = t('explain.count', { count: filteredRules.length });
  const filteredLabel = filteredRules.length !== rules.length
    ? ` ${t('explain.filteredFrom', { total: rules.length })}`
    : '';

  return (
    <div className="flex flex-col h-full relative">
      <ExplainFilters
        filters={filters}
        timezone={timezone}
        selectedCount={selectedIds.size}
        hasSelection={selected !== null}
        onFilterChange={setFilters}
        onTimezoneChange={setTimezone}
        onSearch={handleSearch}
        onAdd={handleAdd}
        onDeleteSelected={handleDeleteSelected}
        onRefresh={fetchRules}
      />

      <div className="px-4 py-1 text-xs text-muted-foreground bg-muted border-b border-border flex items-center gap-3">
        {loading && <span className="text-blue-500">{t('explain.loading')}</span>}
        {!loading && (
          <span>{countLabel}{filteredLabel}</span>
        )}
        {fetchError && <span className="text-red-500 flex items-center gap-1">⚠ {fetchError}</span>}
      </div>

      <div className="flex-1 overflow-auto bg-card">
        {loading && rules.length === 0 ? (
          <div className="flex items-center justify-center py-20 text-muted-foreground text-sm">{t('explain.loading')}</div>
        ) : (
          <ExplainTable
            rules={pagedRules}
            selected={selected}
            selectedIds={selectedIds}
            timezone={timezone}
            minRows={pageSize}
            onRowClick={handleRowClick}
            onCheckboxChange={handleCheckboxChange}
            onSelectAll={handleSelectAll}
          />
        )}
      </div>

      <Pagination
        page={safePage}
        pageSize={pageSize}
        total={filteredRules.length}
        onPageChange={setPage}
        onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
      />

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
