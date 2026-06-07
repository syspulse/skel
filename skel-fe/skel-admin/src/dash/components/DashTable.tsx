import React from 'react';
import { useTranslation } from 'react-i18next';
import type { DashLayout } from '../types';
import { getTimezoneShortLabel } from '../../components/timezone';
import { TimestampCell } from '../../components/TimestampCell';
import { TABLE_TD, TABLE_TH } from '../../constants/table';

const COL_COUNT = 6;

interface DashTableProps {
  dashes: DashLayout[];
  selected: DashLayout | null;
  selectedIds: Set<string>;
  timezone: string;
  minRows: number;
  onRowClick: (dash: DashLayout) => void;
  onCheckboxChange: (dash: DashLayout, checked: boolean) => void;
  onSelectAll: (checked: boolean) => void;
}

export function DashTable({
  dashes,
  selected,
  selectedIds,
  timezone,
  minRows,
  onRowClick,
  onCheckboxChange,
  onSelectAll,
}: DashTableProps) {
  const { t } = useTranslation();
  const allChecked = dashes.length > 0 && dashes.every((d) => selectedIds.has(d.id));
  const someChecked = dashes.some((d) => selectedIds.has(d.id));
  const padCount = Math.max(0, minRows - dashes.length);

  return (
    <table className="w-full table-fixed">
        <thead>
          <tr className="bg-nav text-nav-fg">
            <th className={`w-10 ${TABLE_TH} text-center`}>
              <input
                type="checkbox"
                checked={allChecked}
                ref={(el) => { if (el) el.indeterminate = someChecked && !allChecked; }}
                onChange={(e) => onSelectAll(e.target.checked)}
                className="cursor-pointer"
              />
            </th>
            <th className={`w-32 ${TABLE_TH} text-left`}>
              {t('dash.fields.ts')}{timezone !== 'local' ? ` (${getTimezoneShortLabel(timezone)})` : ''}
            </th>
            <th className={`w-64 ${TABLE_TH} text-left`}>{t('dash.fields.id')}</th>
            <th className={`w-40 ${TABLE_TH} text-left`}>{t('dash.fields.name')}</th>
            <th className={`w-24 ${TABLE_TH} text-left`}>{t('dash.fields.tags')}</th>
            <th className={`${TABLE_TH} text-left`}>{t('dash.fields.desc')}</th>
          </tr>
        </thead>
        <tbody>
          {dashes.length === 0 && (
            <tr className="border-b border-border">
              <td colSpan={COL_COUNT} className={`${TABLE_TD} text-center text-muted-foreground`}>
                {t('dash.noDashes')}
              </td>
            </tr>
          )}
          {dashes.map((dash, idx) => {
            const isChecked = selectedIds.has(dash.id);
            const isSelected = selected !== null && selected.id === dash.id;
            const rowClass = [
              'cursor-pointer transition-colors border-b border-border',
              isSelected
                ? 'bg-blue-100 hover:bg-blue-100'
                : isChecked
                ? 'bg-blue-50 hover:bg-blue-100'
                : idx % 2 === 0
                ? 'bg-card hover:bg-muted'
                : 'bg-muted hover:bg-muted-hover',
            ].join(' ');

            return (
              <tr key={dash.id} className={rowClass} onClick={() => onRowClick(dash)}>
                <td className={`${TABLE_TD} text-center`} onClick={(e) => e.stopPropagation()}>
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={(e) => onCheckboxChange(dash, e.target.checked)}
                    className="cursor-pointer"
                  />
                </td>
                <TimestampCell ts={dash.ts0} timezone={timezone} />
                <td className={`${TABLE_TD} text-muted-foreground truncate font-mono`}>
                  {dash.id}
                </td>
                <td className={`${TABLE_TD} text-foreground truncate`}>
                  {dash.name || <span className="opacity-30">—</span>}
                </td>
                <td className={`${TABLE_TD} text-muted-foreground truncate`}>
                  {dash.tags?.join(', ') || <span className="opacity-30">—</span>}
                </td>
                <td className={`${TABLE_TD} text-muted-foreground truncate`}>
                  {dash.desc || <span className="opacity-30">—</span>}
                </td>
              </tr>
            );
          })}
          {Array.from({ length: padCount }, (_, i) => (
            <tr key={`pad-${i}`} className={`border-b border-border ${(dashes.length + i) % 2 === 0 ? 'bg-card' : 'bg-muted'}`}>
              <td colSpan={COL_COUNT} className={`${TABLE_TD} select-none`}>&nbsp;</td>
            </tr>
          ))}
        </tbody>
    </table>
  );
}
