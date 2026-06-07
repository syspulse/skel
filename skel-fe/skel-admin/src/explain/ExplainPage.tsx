import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from './api';
import { useAuth } from '../auth/useAuth';
import { useNotifications } from '../notifications/NotificationContext';
import { usePageSize, PAGE_SIZE_ALL } from '../settings/PageSizeContext';
import { Pagination } from '../components/Pagination';
import { ModulePage, OVERVIEW_TAB } from '../components/ModulePage';
import { ExplainFilters, FilterState } from './components/ExplainFilters';
import { ExplainSlider } from './components/ExplainSlider';
import { ExplainTable } from './components/ExplainTable';
import type { Explain, ExplainCreateReq, ExplainUpdateReq } from './types';
import type { TimeRange } from '../types';

function rowKey(explain: Explain): string {
  return `${explain.oid ?? ''}_${explain.rid}`;
}

function isInTimeRange(ts0: number, range: TimeRange): boolean {
  if (range.type === 'all') return true;
  const now = Date.now();
  if (range.type === 'last') {
    return ts0 >= now - range.hours * 60 * 60 * 1000;
  }
  return ts0 >= range.start.getTime() && ts0 <= range.end.getTime();
}

export function ExplainPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const { add: notify } = useNotifications();
  const { pageSize, setPageSize } = usePageSize();
  const [page, setPage] = useState(1);
  const [explains, setExplains] = useState<Explain[]>([]);
  const [total, setTotal] = useState(0);
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
    timeRange: { type: 'all' } as TimeRange,
  });

  const fetchExplains = useCallback(async (
    oid: string, rid: string, search: string, pg: number, ps: number,
  ) => {
    setLoading(true);
    setFetchError(null);
    try {
      const from = ps !== PAGE_SIZE_ALL ? (pg - 1) * ps : undefined;
      const size = ps !== PAGE_SIZE_ALL ? ps : undefined;
      const result = search.trim()
        ? await api.searchExplains(token, search.trim(), from, size)
        : await api.listExplains(token, oid || undefined, rid || undefined, from, size);
      const sorted = [...(result.data ?? [])].sort((a, b) => b.ts0 - a.ts0);
      setExplains(sorted);
      setTotal(result.total ?? sorted.length);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setFetchError(msg);
      notify('error', t('explain.errorLoad'), msg);
    } finally {
      setLoading(false);
    }
  }, [token, notify, t]);

  useEffect(() => {
    fetchExplains(filters.oid, filters.rid, activeSearch, page, pageSize);
  }, [fetchExplains, filters.oid, filters.rid, activeSearch, page, pageSize]);

  const filteredExplains = useMemo(() => {
    if (filters.timeRange.type === 'all') return explains;
    return explains.filter((explain) => isInTimeRange(explain.ts0, filters.timeRange));
  }, [explains, filters.timeRange]);

  const handleFilterChange = useCallback((newFilters: FilterState) => {
    if (newFilters.oid !== filters.oid || newFilters.rid !== filters.rid) {
      setPage(1);
    }
    setFilters(newFilters);
  }, [filters.oid, filters.rid]);

  const handleSearch = useCallback((query: string) => {
    setActiveSearch(query);
    setPage(1);
  }, []);

  const handleRefresh = useCallback(() => {
    fetchExplains(filters.oid, filters.rid, activeSearch, page, pageSize);
  }, [fetchExplains, filters.oid, filters.rid, activeSearch, page, pageSize]);

  const handleRowClick = (explain: Explain) => {
    setSelected(explain); setAddMode(false); setSliderOpen(true);
  };

  const handleCheckboxChange = (explain: Explain, checked: boolean) => {
    const key = rowKey(explain);
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(key); else next.delete(key);
      return next;
    });
  };

  const handleSelectAll = (checked: boolean) => {
    setSelectedIds(checked ? new Set(filteredExplains.map(rowKey)) : new Set());
  };

  const handleAdd = () => { setSelected(null); setAddMode(true); setSliderOpen(true); };

  const handleCloseSlider = () => { setSliderOpen(false); setSelected(null); setAddMode(false); };

  const handleCreate = async (rid: string, req: ExplainCreateReq) => {
    await api.createExplain(token, rid, req);
    setSliderOpen(false); setSelected(null);
    await handleRefresh();
  };

  const handleUpdate = async (rid: string, req: ExplainUpdateReq) => {
    await api.updateExplain(token, rid, req);
    setSliderOpen(false); setSelected(null);
    await handleRefresh();
  };

  const handleDelete = async (explain: Explain) => {
    if (!window.confirm(t('explain.confirmDelete', { rid: explain.rid }))) return;
    await api.deleteExplain(token, explain.rid, explain.oid);
    setSliderOpen(false); setSelected(null);
    await handleRefresh();
  };

  const handleDeleteSelected = async () => {
    if (selectedIds.size === 0) return;
    if (!window.confirm(t('explain.confirmDeleteSelected', { count: selectedIds.size }))) return;
    const toDelete = filteredExplains.filter((e) => selectedIds.has(rowKey(e)));
    for (const explain of toDelete) {
      try { await api.deleteExplain(token, explain.rid, explain.oid); } catch { /* continue */ }
    }
    setSelectedIds(new Set()); setSelected(null); setSliderOpen(false);
    await handleRefresh();
  };

  const countLabel = t('explain.count', { count: total });
  const overviewTabs = [{ id: OVERVIEW_TAB, label: t('module.overview') }];

  return (
    <ModulePage
      title={t('nav.explain')}
      tabs={overviewTabs}
      defaultTab={OVERVIEW_TAB}
      padded={false}
      contentClassName="flex flex-col"
    >
      {(tab) => tab === OVERVIEW_TAB && (
        <div className="flex flex-col h-full relative">
          <ExplainFilters
            filters={filters}
            timezone={timezone}
            selectedCount={selectedIds.size}
            hasSelection={selected !== null}
            onFilterChange={handleFilterChange}
            onTimezoneChange={setTimezone}
            onSearch={handleSearch}
            onAdd={handleAdd}
            onDeleteSelected={handleDeleteSelected}
            onRefresh={handleRefresh}
          />

          <div className="px-4 py-1 text-xs text-muted-foreground bg-muted border-b border-border flex items-center gap-3">
            {loading && <span className="text-blue-500">{t('explain.loading')}</span>}
            {!loading && <span>{countLabel}</span>}
            {fetchError && <span className="text-red-500 flex items-center gap-1">⚠ {fetchError}</span>}
          </div>

          <div className="flex-1 overflow-auto bg-card">
            {loading && explains.length === 0 ? (
              <div className="flex items-center justify-center py-20 text-muted-foreground text-sm">{t('explain.loading')}</div>
            ) : (
              <ExplainTable
                explains={filteredExplains}
                selected={selected}
                selectedIds={selectedIds}
                timezone={timezone}
                minRows={pageSize === PAGE_SIZE_ALL ? filteredExplains.length : pageSize}
                onRowClick={handleRowClick}
                onCheckboxChange={handleCheckboxChange}
                onSelectAll={handleSelectAll}
              />
            )}
          </div>

          <Pagination
            page={page}
            pageSize={pageSize}
            total={total}
            onPageChange={setPage}
            onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
          />

          <ExplainSlider
            open={sliderOpen}
            addMode={addMode}
            explain={selected}
            timezone={timezone}
            onClose={handleCloseSlider}
            onCreate={handleCreate}
            onUpdate={handleUpdate}
            onDelete={handleDelete}
          />
        </div>
      )}
    </ModulePage>
  );
}
