import React from 'react';
import { useTranslation } from 'react-i18next';
import { TABLE_ICON_CELL, TABLE_ICON_SIZE, TABLE_TD, TABLE_TH } from '../../constants/table';
import { IconTrash, IconWorkflow } from '../../components/Icons';
import { renderIcon } from '../editor/IconPicker';

export interface TableRow {
  id: number;
  icon?: string;
  name: string;
  title?: string;
  status?: string;
  tags?: string[];
  extra?: string;       // contextual: e.g. "schema #2", "sid 0"
  updatedAt?: number;
}

interface EntityTableProps {
  rows: TableRow[];
  selectedId: number | null;
  extraLabel: string;   // header label for the `extra` column
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

const COL_COUNT = 8;

export function EntityTable({ rows, selectedId, extraLabel, minRows = 12, onRowClick, onDelete }: EntityTableProps) {
  const { t } = useTranslation();
  const padCount = Math.max(0, minRows - rows.length);

  return (
    <table className="w-full table-fixed">
      <thead>
        <tr className="bg-nav text-nav-fg">
          <th className={`w-10 ${TABLE_ICON_CELL}`} />
          <th className={`w-16 ${TABLE_TH} text-left`}>{t('workflow.fields.id')}</th>
          <th className={`w-56 ${TABLE_TH} text-left`}>{t('workflow.fields.name')}</th>
          <th className={`${TABLE_TH} text-left`}>{t('workflow.fields.title')}</th>
          <th className={`w-24 ${TABLE_TH} text-left`}>{t('workflow.fields.status')}</th>
          <th className={`w-40 ${TABLE_TH} text-left`}>{extraLabel}</th>
          <th className={`w-44 ${TABLE_TH} text-left`}>{t('workflow.fields.tags')}</th>
          <th className={`w-12 ${TABLE_TH} text-center`} />
        </tr>
      </thead>
      <tbody>
        {rows.length === 0 && (
          <tr className="border-b border-border">
            <td colSpan={COL_COUNT} className={`${TABLE_TD} text-center text-muted-foreground`}>{t('common.noData')}</td>
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
                <span className="inline-flex items-center justify-center">
                  {renderIcon(r.icon, TABLE_ICON_SIZE) ?? <IconWorkflow size={TABLE_ICON_SIZE} className="text-blue-500" />}
                </span>
              </td>
              <td className={`${TABLE_TD} text-muted-foreground`}>{r.id}</td>
              <td className={`${TABLE_TD} text-foreground truncate`}>{r.name}</td>
              <td className={`${TABLE_TD} text-muted-foreground truncate`}>{r.title || <span className="opacity-30">—</span>}</td>
              <td className={`${TABLE_TD} ${statusClass(r.status)}`}>{r.status || <span className="opacity-30">—</span>}</td>
              <td className={`${TABLE_TD} text-muted-foreground truncate`}>{r.extra || <span className="opacity-30">—</span>}</td>
              <td className={`${TABLE_TD} text-muted-foreground truncate`}>{r.tags && r.tags.length > 0 ? r.tags.join(', ') : <span className="opacity-30">—</span>}</td>
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
            <td colSpan={COL_COUNT - 1} className={`${TABLE_TD} select-none`}>&nbsp;</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
