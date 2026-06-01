import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import * as api from './api';
import { useAuth } from '../auth/useAuth';
import { useNotifications } from '../notifications/NotificationContext';
import { DashFilters, DashFilterState } from './components/DashFilters';
import { DashSlider } from './components/DashSlider';
import { DashTable } from './components/DashTable';
import type { DashLayout, DashCreateReq, DashUpdateReq } from './types';

export function DashPage() {
  const { t } = useTranslation();
  const { token } = useAuth();
  const { add: notify } = useNotifications();
  const [dashes, setDashes] = useState<DashLayout[]>([]);
  const [loading, setLoading] = useState(false);
  const [fetchError, setFetchError] = useState<string | null>(null);

  const [selected, setSelected] = useState<DashLayout | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [sliderOpen, setSliderOpen] = useState(false);
  const [addMode, setAddMode] = useState(false);
  const [timezone, setTimezone] = useState('local');

  const [filters, setFilters] = useState<DashFilterState>({ search: '' });

  const fetchDashes = useCallback(async () => {
    setLoading(true);
    setFetchError(null);
    try {
      const result = await api.listDashes(token);
      const sorted = [...(result.data ?? [])].sort((a, b) => b.ts0 - a.ts0);
      setDashes(sorted);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      setFetchError(msg);
      notify('error', t('dash.errorLoad'), msg);
    } finally {
      setLoading(false);
    }
  }, [token, notify, t]);

  useEffect(() => { fetchDashes(); }, [fetchDashes]);

  const filteredDashes = useMemo(() => {
    const q = filters.search.toLowerCase().trim();
    if (!q) return dashes;
    return dashes.filter((d) => {
      return (
        (d.name ?? '').toLowerCase().includes(q) ||
        (d.desc ?? '').toLowerCase().includes(q) ||
        (d.tags ?? []).some(tag => tag.toLowerCase().includes(q)) ||
        d.id.toLowerCase().includes(q)
      );
    });
  }, [dashes, filters]);

  const handleRowClick = (dash: DashLayout) => {
    setSelected(dash); setAddMode(false); setSliderOpen(true);
  };

  const handleCheckboxChange = (dash: DashLayout, checked: boolean) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(dash.id); else next.delete(dash.id);
      return next;
    });
  };

  const handleSelectAll = (checked: boolean) => {
    setSelectedIds(checked ? new Set(filteredDashes.map(d => d.id)) : new Set());
  };

  const handleAdd = () => { setSelected(null); setAddMode(true); setSliderOpen(true); };

  const handleCloseSlider = () => { setSliderOpen(false); setSelected(null); setAddMode(false); };

  const handleCreate = async (req: DashCreateReq) => {
    await api.createDash(token, req);
    setSliderOpen(false); setSelected(null); await fetchDashes();
  };

  const handleUpdate = async (id: string, req: DashUpdateReq) => {
    await api.updateDash(token, id, req);
    setSliderOpen(false); setSelected(null); await fetchDashes();
  };

  const handleDelete = async (dash: DashLayout) => {
    if (!window.confirm(t('dash.confirmDelete', { name: dash.name || dash.id }))) return;
    await api.deleteDash(token, dash.id);
    setSliderOpen(false); setSelected(null); await fetchDashes();
  };

  const handleDeleteSelected = async () => {
    if (selectedIds.size === 0) return;
    if (!window.confirm(t('dash.confirmDeleteSelected', { count: selectedIds.size }))) return;
    const toDelete = filteredDashes.filter((d) => selectedIds.has(d.id));
    for (const dash of toDelete) {
      try { await api.deleteDash(token, dash.id); } catch { /* continue */ }
    }
    setSelectedIds(new Set()); setSelected(null); setSliderOpen(false);
    await fetchDashes();
  };

  const countLabel = t('dash.count', { count: filteredDashes.length });
  const filteredLabel = filteredDashes.length !== dashes.length
    ? ` ${t('dash.filteredFrom', { total: dashes.length })}`
    : '';

  return (
    <div className="flex flex-col h-full relative">
      <DashFilters
        filters={filters}
        timezone={timezone}
        selectedCount={selectedIds.size}
        hasSelection={selected !== null}
        onFilterChange={setFilters}
        onTimezoneChange={setTimezone}
        onAdd={handleAdd}
        onDeleteSelected={handleDeleteSelected}
        onRefresh={fetchDashes}
      />

      <div className="px-4 py-1 text-xs text-muted-foreground bg-muted border-b border-border flex items-center gap-3">
        {loading && <span className="text-blue-500">{t('dash.loading')}</span>}
        {!loading && (
          <span>{countLabel}{filteredLabel}</span>
        )}
        {fetchError && <span className="text-red-500 flex items-center gap-1">⚠ {fetchError}</span>}
      </div>

      <div className="flex-1 overflow-auto bg-card">
        {loading && dashes.length === 0 ? (
          <div className="flex items-center justify-center py-20 text-muted-foreground text-sm">{t('dash.loading')}</div>
        ) : (
          <DashTable
            dashes={filteredDashes}
            selected={selected}
            selectedIds={selectedIds}
            timezone={timezone}
            onRowClick={handleRowClick}
            onCheckboxChange={handleCheckboxChange}
            onSelectAll={handleSelectAll}
          />
        )}
      </div>

      <DashSlider
        open={sliderOpen}
        addMode={addMode}
        dash={selected}
        onClose={handleCloseSlider}
        onCreate={handleCreate}
        onUpdate={handleUpdate}
        onDelete={handleDelete}
      />
    </div>
  );
}
