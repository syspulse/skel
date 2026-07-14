import React, { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { TABLE_ICON_CELL, TABLE_TD, TABLE_TH } from '../../../constants/table';
import { IconTrash } from '../../../components/Icons';
import { TimestampCell } from '../../../components/TimestampCell';
import { getTimezoneShortLabel } from '../../../components/timezone';
import { renderIconFill } from '../../../components/IconPicker';
import { statusChipStyle } from '../status';

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
  onRowDoubleClick?: (id: number) => void;
  onDelete: (id: number) => void;
}

export function EntityTable({ rows, columns, selectedId, defaultIcon, timezone, minRows = 12, onRowClick, onRowDoubleClick, onDelete }: EntityTableProps) {
  const { t } = useTranslation();
  const padCount = Math.max(0, minRows - rows.length);
  const colCount = 7 + columns.length; // icon,id,name + dynamic + status,ts,tags,delete

  // Single/double click disambiguation: when a double-click handler exists, defer the single-click
  // action so it can be cancelled by a double-click (otherwise the first click opens the slider whose
  // backdrop then swallows the second click and the dblclick never reaches the row).
  const clickTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const handleRowClick = (id: number) => {
    if (!onRowDoubleClick) { onRowClick(id); return; }
    if (clickTimer.current) clearTimeout(clickTimer.current);
    clickTimer.current = setTimeout(() => { clickTimer.current = null; onRowClick(id); }, 220);
  };
  const handleRowDoubleClick = (id: number) => {
    if (clickTimer.current) { clearTimeout(clickTimer.current); clickTimer.current = null; }
    onRowDoubleClick?.(id);
  };

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
          <th className={`w-32 ${TABLE_TH} text-left`}>{t('workflow.fields.status')}</th>
          <th className={`w-40 ${TABLE_TH} text-left`}>
            {t('workflow.fields.ts')}{timezone !== 'local' ? ` (${getTimezoneShortLabel(timezone)})` : ''}
          </th>
          <th className={`w-64 ${TABLE_TH} text-left`}>{t('workflow.fields.tags')}</th>
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
            <tr key={r.id} className={rowClass} onClick={() => handleRowClick(r.id)} onDoubleClick={() => handleRowDoubleClick(r.id)}>
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
              <td className={TABLE_TD}>
                {r.status ? (
                  <span className="text-[11px] px-1.5 py-0.5 rounded font-semibold" style={statusChipStyle(r.status)} title={`status: ${r.status}`}>
                    {r.status}
                  </span>
                ) : ''}
              </td>
              {r.ts != null
                ? <TimestampCell ts={r.ts} timezone={timezone} />
                : <td className={`${TABLE_TD} text-muted-foreground`} />}
              <td className={TABLE_TD}>
                {r.tags && r.tags.length > 0 ? (
                  <div className="flex flex-wrap items-center gap-1">
                    {r.tags.map((tag, i) => (
                      <span key={i} className="inline-flex items-center text-[11px] px-1.5 py-0.5 rounded border bg-muted border-border text-foreground max-w-[120px] truncate" title={tag}>{tag}</span>
                    ))}
                  </div>
                ) : ''}
              </td>
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
