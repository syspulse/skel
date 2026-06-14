import React, { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { EditableCombo } from '../../../components/EditableCombo';
import { FilterField } from '../../../components/FilterField';
import type { DispatcherEvent } from '../types';
import type { DispatcherFilterState } from '../filterEvents';
import { uniqueFieldValues } from '../filterEvents';

interface DispatcherFiltersProps {
  filters: DispatcherFilterState;
  history: readonly DispatcherEvent[];
  onFilterChange: (filters: DispatcherFilterState) => void;
}

function ComboFilter({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (value: string) => void;
}) {
  return (
    <FilterField label={label}>
      <EditableCombo value={value} onChange={onChange} options={options} placeholder="..." className="w-28" />
    </FilterField>
  );
}

export function DispatcherFilters({ filters, history, onFilterChange }: DispatcherFiltersProps) {
  const { t } = useTranslation();

  const srcOptions = useMemo(() => uniqueFieldValues(history, (e) => e.src), [history]);
  const sysOptions = useMemo(() => uniqueFieldValues(history, (e) => e.sys), [history]);
  const typOptions = useMemo(() => uniqueFieldValues(history, (e) => e.typ), [history]);
  const cmdOptions = useMemo(() => uniqueFieldValues(history, (e) => e.cmd), [history]);
  const sevOptions = useMemo(() => uniqueFieldValues(history, (e) => e.sev), [history]);
  const dstOptions = useMemo(() => uniqueFieldValues(history, (e) => e.dst), [history]);

  const set = (key: keyof DispatcherFilterState, value: string) => {
    onFilterChange({ ...filters, [key]: value });
  };

  return (
    <div className="filter-bar">
      <ComboFilter
        label={t('dispatcher.fields.src')}
        value={filters.src}
        options={srcOptions}
        onChange={(v) => set('src', v)}
      />
      <ComboFilter
        label={t('dispatcher.fields.sys')}
        value={filters.sys}
        options={sysOptions}
        onChange={(v) => set('sys', v)}
      />
      <ComboFilter
        label={t('dispatcher.fields.typ')}
        value={filters.typ}
        options={typOptions}
        onChange={(v) => set('typ', v)}
      />
      <ComboFilter
        label={t('dispatcher.fields.cmd')}
        value={filters.cmd}
        options={cmdOptions}
        onChange={(v) => set('cmd', v)}
      />
      <ComboFilter
        label={t('dispatcher.fields.sev')}
        value={filters.sev}
        options={sevOptions}
        onChange={(v) => set('sev', v)}
      />
      <ComboFilter
        label={t('dispatcher.fields.dst')}
        value={filters.dst}
        options={dstOptions}
        onChange={(v) => set('dst', v)}
      />
    </div>
  );
}
