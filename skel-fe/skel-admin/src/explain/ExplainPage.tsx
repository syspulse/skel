import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from './api';
import { useAuth } from '../auth/useAuth';
import { useNotifications } from '../notifications/NotificationContext';
import { ExplainFilters, FilterState } from './components/ExplainFilters';
import { ExplainSlider } from './components/ExplainSlider';
import { ExplainTable } from './components/ExplainTable';
import type { Explain, ExplainCreateReq, ExplainUpdateReq } from './types';
import type { TimeRange } from '../types';

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
  const { t } = useTranslation();
  const { token } = useAuth();
  const { add: notify } = useNotifications();
  const [rules, setRules] = useState<Explain[]>([]);
  const [loading, setLoading] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [selected, setSelected] = useState<Explain | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [sliderOpen, setSliderOpen] = useState(false);
  const [addMode, setAddMode] = useState(false);
  const [timezone, setTimezone] = useState('local');

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
      const sorted = [...(result.data ?? [])].sort((a, b) => b.ts0 - a.ts0);
      setRules(sorted);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setFetchError(msg);
      notify('error', t('explain.errorLoad'), msg);
    } finally {
      setLoading(false);
    }
  }, [token, notify, t]);

  useEffect(() => { fetchRules(); }, [fetchRules]);

  const filteredRules = useMemo(() => {
    return rules.filter((rule) => {
      if (filters.oid && !(rule.oid ?? '').toLowerCase().includes(filters.oid.toLowerCase())) return false;
      if (filters.rid && !rule.rid.toLowerCase().includes(filters.rid.toLowerCase())) return false;
      if (!isInTimeRange(rule.ts0, filters.timeRange)) return false;
      return true;
    });
  }, [rules, filters]);

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
            rules={filteredRules}
            selected={selected}
            selectedIds={selectedIds}
            timezone={timezone}
            onRowClick={handleRowClick}
            onCheckboxChange={handleCheckboxChange}
            onSelectAll={handleSelectAll}
          />
        )}
      </div>

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
