import React from 'react';
import { useTranslation } from 'react-i18next';
import type { Explain } from '../types';
import { IconLamp } from '../../../components/Icons';
import { getTimezoneShortLabel } from '../../../components/timezone';
import { TimestampCell } from '../../../components/TimestampCell';
import { TABLE_ICON_CELL, TABLE_ICON_SIZE, TABLE_TD, TABLE_TH } from '../../../constants/table';

function ExplainIcon({ meta }: { meta?: Record<string, unknown> | null }) {
  const icon = meta?.icon;
  if (typeof icon === 'string' && icon.trim()) {
    const s = icon.trim();
    if (s.toLowerCase().startsWith('<svg')) {
      return (
        <span
          className="inline-flex items-center justify-center shrink-0 [&>svg]:w-full [&>svg]:h-full"
          style={{ width: TABLE_ICON_SIZE, height: TABLE_ICON_SIZE }}
          dangerouslySetInnerHTML={{ __html: s }}
        />
      );
    }
    if (s.startsWith('http') || s.startsWith('/') || s.startsWith('data:')) {
      return (
        <img
          src={s}
          alt="icon"
          width={TABLE_ICON_SIZE}
          height={TABLE_ICON_SIZE}
          className="object-contain shrink-0"
        />
      );
    }
  }
  return <IconLamp size={TABLE_ICON_SIZE} className="text-amber-500 shrink-0" />;
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
          <tr className="bg-nav text-nav-fg">
            <th className={`w-10 ${TABLE_TH} text-center`}>
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
            <th className={`w-10 ${TABLE_ICON_CELL}`} aria-label={t('explain.fields.icon')} />
            <th className={`w-32 ${TABLE_TH} text-left`}>
              {t('explain.fields.ts')}{timezone !== 'local' ? ` (${getTimezoneShortLabel(timezone)})` : ''}
            </th>
            <th className={`w-24 ${TABLE_TH} text-left`}>{t('explain.fields.oid')}</th>
            <th className={`w-48 ${TABLE_TH} text-left`}>{t('explain.fields.rid')}</th>
            <th className={`w-64 ${TABLE_TH} text-left`}>{t('explain.fields.name')}</th>
            <th className={`${TABLE_TH} text-left`}>{t('explain.fields.desc')}</th>
          </tr>
        </thead>
        <tbody>
          {explains.length === 0 && (
            <tr className="border-b border-border">
              <td colSpan={COL_COUNT} className={`${TABLE_TD} text-center text-muted-foreground`}>
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
                <td className={`${TABLE_TD} text-center`} onClick={(e) => e.stopPropagation()}>
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={(e) => onCheckboxChange(explain, e.target.checked)}
                    className="cursor-pointer"
                  />
                </td>
                <td className={TABLE_ICON_CELL}>
                  <span className="inline-flex items-center justify-center">
                    <ExplainIcon meta={explain.meta as Record<string, unknown> | undefined} />
                  </span>
                </td>
                <TimestampCell ts={explain.ts} timezone={timezone} />
                <td className={`${TABLE_TD} text-muted-foreground max-w-[140px] truncate`}>
                  {explain.oid || <span className="opacity-30">—</span>}
                </td>
                <td className={`${TABLE_TD} text-foreground max-w-[180px] truncate`}>
                  {explain.rid}
                </td>
                <td className={`${TABLE_TD} text-foreground max-w-[160px] truncate`}>
                  {explain.name || <span className="opacity-30">—</span>}
                </td>
                <td className={`${TABLE_TD} text-muted-foreground max-w-[240px] truncate`}>
                  {explain.desc || <span className="opacity-30">—</span>}
                </td>
              </tr>
            );
          })}
          {Array.from({ length: padCount }, (_, i) => (
            <tr key={`pad-${i}`} className={`border-b border-border ${(explains.length + i) % 2 === 0 ? 'bg-card' : 'bg-muted'}`}>
              <td className={TABLE_TD} />
              <td className={TABLE_ICON_CELL} />
              <td colSpan={COL_COUNT - 2} className={`${TABLE_TD} select-none`}>&nbsp;</td>
            </tr>
          ))}
        </tbody>
    </table>
  );
}
