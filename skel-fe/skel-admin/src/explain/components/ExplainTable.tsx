import React from 'react';
import { useTranslation } from 'react-i18next';
import type { Explain } from '../types';
import { IconLamp } from '../../components/Icons';

function ExplainIcon({ meta }: { meta?: Record<string, unknown> | null }) {
  const icon = meta?.icon;
  if (typeof icon === 'string' && icon.trim()) {
    const s = icon.trim();
    if (s.toLowerCase().startsWith('<svg')) {
      return (
        <span
          className="inline-flex items-center justify-center w-[18px] h-[18px] [&>svg]:w-full [&>svg]:h-full"
          dangerouslySetInnerHTML={{ __html: s }}
        />
      );
    }
    if (s.startsWith('http') || s.startsWith('/') || s.startsWith('data:')) {
      return <img src={s} alt="icon" width={18} height={18} className="object-contain" />;
    }
  }
  return <IconLamp size={18} className="text-amber-500" />;
}

const MONTHS = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

const TZ_LABELS: Record<string, string> = {
  'local':                'local',
  'UTC':                  'GMT',
  'Europe/Berlin':        'CET',
  'America/New_York':     'ET',
  'America/Chicago':      'CT',
  'America/Denver':       'MT',
  'America/Los_Angeles':  'PT',
  'Asia/Hong_Kong':       'HKT',
};

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

function rowKey(explain: Explain): string {
  return `${explain.oid ?? ''}_${explain.rid}`;
}

const COL_COUNT = 7;

interface ExplainTableProps {
  explains: Explain[];
  selected: Explain | null;
  selectedIds: Set<string>;
  timezone: string;
  minRows: number;
  onRowClick: (explain: Explain) => void;
  onCheckboxChange: (explain: Explain, checked: boolean) => void;
  onSelectAll: (checked: boolean) => void;
}

export function ExplainTable({
  explains,
  selected,
  selectedIds,
  timezone,
  minRows,
  onRowClick,
  onCheckboxChange,
  onSelectAll,
}: ExplainTableProps) {
  const { t } = useTranslation();
  const allChecked = explains.length > 0 && explains.every((e) => selectedIds.has(rowKey(e)));
  const someChecked = explains.some((e) => selectedIds.has(rowKey(e)));
  const padCount = Math.max(0, minRows - explains.length);

  return (
    <table className="w-full table-fixed">
        <thead>
          <tr className="bg-nav text-nav-fg text-xs">
            <th className="w-10 px-3 py-2 text-center">
              <input
                type="checkbox"
                checked={allChecked}
                ref={(el) => {
                  if (el) el.indeterminate = someChecked && !allChecked;
                }}
                onChange={(e) => onSelectAll(e.target.checked)}
                className="cursor-pointer"
              />
            </th>
            <th className="w-10 px-2 py-2 text-center">{t('explain.fields.icon')}</th>
            <th className="w-32 px-3 py-2 text-left">
              {t('explain.fields.ts')}{timezone !== 'local' ? ` (${TZ_LABELS[timezone] ?? timezone})` : ''}
            </th>
            <th className="w-24 px-3 py-2 text-left">{t('explain.fields.oid')}</th>
            <th className="w-48 px-3 py-2 text-left">{t('explain.fields.rid')}</th>
            <th className="w-64 px-3 py-2 text-left">{t('explain.fields.name')}</th>
            <th className="px-3 py-2 text-left">{t('explain.fields.desc')}</th>
          </tr>
        </thead>
        <tbody>
          {explains.length === 0 && (
            <tr className="border-b border-border">
              <td colSpan={COL_COUNT} className="px-3 py-2 text-xs text-center text-muted-foreground">
                {t('common.noData')}
              </td>
            </tr>
          )}
          {explains.map((explain, idx) => {
            const key = rowKey(explain);
            const isChecked = selectedIds.has(key);
            const isSelected = selected !== null && rowKey(selected) === key;
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
              <tr key={key} className={rowClass} onClick={() => onRowClick(explain)}>
                <td className="px-3 py-2 text-center" onClick={(e) => e.stopPropagation()}>
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={(e) => onCheckboxChange(explain, e.target.checked)}
                    className="cursor-pointer"
                  />
                </td>
                <td className="px-2 py-2 text-center">
                  <span className="inline-flex items-center justify-center">
                    <ExplainIcon meta={explain.meta as Record<string, unknown> | undefined} />
                  </span>
                </td>
                <td className="px-3 py-2 whitespace-nowrap text-xs text-muted-foreground">
                  {formatTs(explain.ts0, timezone)}
                </td>
                <td className="px-3 py-2 text-xs text-muted-foreground max-w-[140px] truncate">
                  {explain.oid || <span className="opacity-30">—</span>}
                </td>
                <td className="px-3 py-2 text-xs text-foreground max-w-[180px] truncate">
                  {explain.rid}
                </td>
                <td className="px-3 py-2 text-xs text-foreground max-w-[160px] truncate">
                  {explain.name || <span className="opacity-30">—</span>}
                </td>
                <td className="px-3 py-2 text-xs text-muted-foreground max-w-[240px] truncate">
                  {explain.desc || <span className="opacity-30">—</span>}
                </td>
              </tr>
            );
          })}
          {Array.from({ length: padCount }, (_, i) => (
            <tr key={`pad-${i}`} className={`border-b border-border ${(explains.length + i) % 2 === 0 ? 'bg-card' : 'bg-muted'}`}>
              <td className="px-3 py-2" />
              <td className="px-2 py-2 text-center">
                <span className="invisible inline-flex items-center justify-center">
                  <IconLamp size={18} />
                </span>
              </td>
              <td colSpan={COL_COUNT - 2} className="px-3 py-2" />
            </tr>
          ))}
        </tbody>
    </table>
  );
}
