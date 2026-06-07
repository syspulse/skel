import React from 'react';
import { useTranslation } from 'react-i18next';
import type { DashLayout } from '../types';
import { getTimezoneShortLabel } from '../../components/timezone';

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

function formatTs(ts: number, timezone: string): string {
  const d = new Date(ts);
  if (timezone === 'local') {
    const day = d.getDate();
    const mon = MONTHS[d.getMonth()];
    const hh  = String(d.getHours()).padStart(2, '0');
    const mm  = String(d.getMinutes()).padStart(2, '0');
    const ss  = String(d.getSeconds()).padStart(2, '0');
    return `${day} ${mon} ${hh}:${mm}:${ss}`;
  }
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: timezone,
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(d);
  const get = (type: string) => parts.find(p => p.type === type)?.value ?? '';
  return `${get('day')} ${get('month')} ${get('hour')}:${get('minute')}:${get('second')}`;
}

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
          <tr className="bg-nav text-nav-fg text-xs">
            <th className="w-10 px-3 py-2 text-center">
              <input
                type="checkbox"
                checked={allChecked}
                ref={(el) => { if (el) el.indeterminate = someChecked && !allChecked; }}
                onChange={(e) => onSelectAll(e.target.checked)}
                className="cursor-pointer"
              />
            </th>
            <th className="w-32 px-3 py-2 text-left">
              {t('dash.fields.ts')}{timezone !== 'local' ? ` (${getTimezoneShortLabel(timezone)})` : ''}
            </th>
            <th className="w-64 px-3 py-2 text-left">{t('dash.fields.id')}</th>
            <th className="w-40 px-3 py-2 text-left">{t('dash.fields.name')}</th>
            <th className="w-24 px-3 py-2 text-left">{t('dash.fields.tags')}</th>
            <th className="px-3 py-2 text-left">{t('dash.fields.desc')}</th>
          </tr>
        </thead>
        <tbody>
          {dashes.length === 0 && (
            <tr className="border-b border-border">
              <td colSpan={COL_COUNT} className="px-3 py-2 text-xs text-center text-muted-foreground">
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
                <td className="px-3 py-2 text-center" onClick={(e) => e.stopPropagation()}>
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={(e) => onCheckboxChange(dash, e.target.checked)}
                    className="cursor-pointer"
                  />
                </td>
                <td className="px-3 py-2 whitespace-nowrap text-xs text-muted-foreground">
                  {formatTs(dash.ts0, timezone)}
                </td>
                <td className="px-3 py-2 text-xs text-muted-foreground truncate font-mono">
                  {dash.id}
                </td>
                <td className="px-3 py-2 text-xs text-foreground truncate">
                  {dash.name || <span className="opacity-30">—</span>}
                </td>
                <td className="px-3 py-2 text-xs text-muted-foreground truncate">
                  {dash.tags?.join(', ') || <span className="opacity-30">—</span>}
                </td>
                <td className="px-3 py-2 text-xs text-muted-foreground truncate">
                  {dash.desc || <span className="opacity-30">—</span>}
                </td>
              </tr>
            );
          })}
          {Array.from({ length: padCount }, (_, i) => (
            <tr key={`pad-${i}`} className={`border-b border-border ${(dashes.length + i) % 2 === 0 ? 'bg-card' : 'bg-muted'}`}>
              {/* &nbsp; with text-xs forces the same line-height as real rows */}
              <td colSpan={COL_COUNT} className="px-3 py-2 text-xs select-none">&nbsp;</td>
            </tr>
          ))}
        </tbody>
    </table>
  );
}
