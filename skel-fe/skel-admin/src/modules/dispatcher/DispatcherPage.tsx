import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ModulePage, OVERVIEW_TAB } from '../../components/ModulePage';
import { Pagination } from '../../components/Pagination';
import { usePageSize, PAGE_SIZE_ALL } from '../../settings/PageSizeContext';
import { useDispatcher } from './DispatcherContext';
import { DispatcherStats } from './components/DispatcherStats';
import { DispatcherTable } from './components/DispatcherTable';
import { DispatcherEventSlider } from './components/DispatcherEventSlider';
import { DispatcherFilters } from './components/DispatcherFilters';
import type { DispatcherEvent } from './types';
import {
  EMPTY_DISPATCHER_FILTERS,
  filterDispatcherEvents,
  type DispatcherFilterState,
} from './filterEvents';

export function DispatcherPage() {
  const { t } = useTranslation();
  const { history, connected, wsUrl, messageCount, reconnectAttempts } = useDispatcher();
  const { pageSize, setPageSize } = usePageSize();
  const [page, setPage] = useState(1);
  const [filters, setFilters] = useState<DispatcherFilterState>(EMPTY_DISPATCHER_FILTERS);
  const [selected, setSelected] = useState<DispatcherEvent | null>(null);
  const [sliderOpen, setSliderOpen] = useState(false);

  const tabs = [{ id: OVERVIEW_TAB, label: t('module.overview') }];

  const filteredEvents = useMemo(
    () => filterDispatcherEvents(history, filters),
    [history, filters],
  );

  const total = filteredEvents.length;

  const pageEvents = useMemo(() => {
    if (pageSize === PAGE_SIZE_ALL) return filteredEvents;
    const start = (page - 1) * pageSize;
    return filteredEvents.slice(start, start + pageSize);
  }, [filteredEvents, page, pageSize]);

  useEffect(() => {
    if (!selected) return;
    const updated = history.find((event) => event.id === selected.id);
    if (updated) setSelected(updated);
  }, [history, selected?.id]);

  const handleFilterChange = (next: DispatcherFilterState) => {
    setFilters(next);
    setPage(1);
  };

  const handleRowClick = (event: DispatcherEvent) => {
    setSelected(event);
    setSliderOpen(true);
  };

  const handleCloseSlider = () => {
    setSliderOpen(false);
    setSelected(null);
  };

  return (
    <ModulePage
      title={t('nav.dispatcher')}
      tabs={tabs}
      defaultTab={OVERVIEW_TAB}
      padded={false}
      contentClassName="flex flex-col"
      afterTabs={(tab) => tab === OVERVIEW_TAB ? (
        <>
          <DispatcherStats
            wsUrl={wsUrl}
            connected={connected}
            messageCount={messageCount}
            reconnectAttempts={reconnectAttempts}
          />
          <DispatcherFilters
            filters={filters}
            history={history}
            onFilterChange={handleFilterChange}
          />
        </>
      ) : null}
    >
      {(tab) => tab === OVERVIEW_TAB && (
        <div className="flex flex-col h-full relative">
          <div className="flex-1 overflow-auto bg-card">
            <DispatcherTable
              events={pageEvents}
              selected={selected}
              onRowClick={handleRowClick}
            />
          </div>

          <Pagination
            page={page}
            pageSize={pageSize}
            total={total}
            onPageChange={setPage}
            onPageSizeChange={(s) => { setPageSize(s); setPage(1); }}
            footerLeft={
              <>
                <span>{t('dispatcher.total', { count: total })}</span>
                {wsUrl && !connected && (
                  <span className="text-red-500 flex items-center gap-1">
                    ⚠ {t('dispatcher.wsDisconnected')}
                  </span>
                )}
              </>
            }
          />

          <DispatcherEventSlider
            open={sliderOpen}
            event={selected}
            onClose={handleCloseSlider}
          />
        </div>
      )}
    </ModulePage>
  );
}
