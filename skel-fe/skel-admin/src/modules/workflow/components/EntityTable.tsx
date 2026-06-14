import React from 'react';
import { useTranslation } from 'react-i18next';
import { TABLE_ICON_CELL, TABLE_TD, TABLE_TH } from '../../../constants/table';
import { IconTrash } from '../../../components/Icons';
import { TimestampCell } from '../../../components/TimestampCell';
import { getTimezoneShortLabel } from '../../../components/timezone';
import { renderIconFill } from '../../../components/IconPicker';

export interface TableColumn {
  key: string;
  label: string;
  width?: string; // tailwind width class; omit for a flexible column
}

export interface TableRow {
  id: number;
  icon?: string;
  name: string;
  status?: string;
  tags?: string[];
  ts?: number;                    // updatedAt (shown in the `ts` column)
  cells?: Record<string, string>; // values for the dynamic columns, keyed by column.key
}

interface EntityTableProps {
  rows: TableRow[];
  columns: TableColumn[];   // dynamic columns rendered between `name` and `status`
  selectedId: number | null;
  defaultIcon: string;      // svg string fallback when a row has no icon
  timezone: string;
  minRows?: number;
  onRowClick: (id: number) => void;
  onDelete: (id: number) => void;
}

function statusClass(status?: string): string {
  switch ((status ?? '').toUpperCase()) {
    case 'ACTIVE': return 'text-emerald-600';
    case 'DISABLED': return 'text-amber-600';
    case 'DELETED': return 'text-red-600';
    default: return 'text-muted-foreground';
  }
}

export function EntityTable({ rows, columns, selectedId, defaultIcon, timezone, minRows = 12, onRowClick, onDelete }: EntityTableProps) {
  const { t } = useTranslation();
  const padCount = Math.max(0, minRows - rows.length);
  const colCount = 7 + columns.length; // icon,id,name + dynamic + status,ts,tags,delete

  return (
    <table className="w-full table-fixed">
      <thead>
        <tr className="bg-nav text-nav-fg">
          <th className={`w-10 ${TABLE_ICON_CELL}`} />
          <th className={`w-16 ${TABLE_TH} text-left`}>{t('workflow.fields.id')}</th>
          <th className={`w-56 ${TABLE_TH} text-left`}>{t('workflow.fields.name')}</th>
          {columns.map((c) => (
            <th key={c.key} className={`${c.width ?? ''} ${TABLE_TH} text-left`}>{c.label}</th>
          ))}
          <th className={`w-24 ${TABLE_TH} text-left`}>{t('workflow.fields.status')}</th>
          <th className={`w-40 ${TABLE_TH} text-left`}>
            {t('workflow.fields.ts')}{timezone !== 'local' ? ` (${getTimezoneShortLabel(timezone)})` : ''}
          </th>
          <th className={`w-40 ${TABLE_TH} text-left`}>{t('workflow.fields.tags')}</th>
          <th className={`w-12 ${TABLE_TH} text-center`} />
        </tr>
      </thead>
      <tbody>
        {rows.length === 0 && (
          <tr className="border-b border-border">
            <td colSpan={colCount} className={`${TABLE_TD} text-center text-muted-foreground`}>{t('common.noData')}</td>
          </tr>
        )}
        {rows.map((r, idx) => {
          const isSelected = selectedId === r.id;
          const rowClass = [
            'cursor-pointer transition-colors border-b border-border',
            isSelected ? 'bg-blue-100 hover:bg-blue-100'
              : idx % 2 === 0 ? 'bg-card hover:bg-muted' : 'bg-muted hover:bg-muted-hover',
          ].join(' ');
          return (
            <tr key={r.id} className={rowClass} onClick={() => onRowClick(r.id)}>
              <td className={TABLE_ICON_CELL}>
                <span className="table-icon text-foreground">
                  {renderIconFill(r.icon && r.icon.trim() ? r.icon : defaultIcon)}
                </span>
              </td>
              <td className={`${TABLE_TD} text-muted-foreground`}>{r.id}</td>
              <td className={`${TABLE_TD} text-foreground truncate`}>{r.name}</td>
              {columns.map((c) => (
                <td key={c.key} className={`${TABLE_TD} text-muted-foreground truncate`}>{r.cells?.[c.key] ?? ''}</td>
              ))}
              <td className={`${TABLE_TD} ${statusClass(r.status)}`}>{r.status ?? ''}</td>
              {r.ts != null
                ? <TimestampCell ts={r.ts} timezone={timezone} />
                : <td className={`${TABLE_TD} text-muted-foreground`} />}
              <td className={`${TABLE_TD} text-muted-foreground truncate`}>{r.tags && r.tags.length > 0 ? r.tags.join(', ') : ''}</td>
              <td className={`${TABLE_TD} text-center`} onClick={(e) => e.stopPropagation()}>
                <button onClick={() => onDelete(r.id)} className="text-muted-foreground hover:text-red-500 p-0.5 rounded transition-colors" title={t('common.delete')}>
                  <IconTrash size={13} />
                </button>
              </td>
            </tr>
          );
        })}
        {Array.from({ length: padCount }, (_, i) => (
          <tr key={`pad-${i}`} className={`border-b border-border ${(rows.length + i) % 2 === 0 ? 'bg-card' : 'bg-muted'}`}>
            <td className={TABLE_ICON_CELL} />
            <td colSpan={colCount - 1} className={`${TABLE_TD} select-none`}>&nbsp;</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
