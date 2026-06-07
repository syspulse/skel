import React from 'react';
import { useTranslation } from 'react-i18next';
import type { DispatcherEvent } from '../types';
import { TimestampCell } from '../../components/TimestampCell';
import { DEFAULT_TIMEZONE } from '../../components/timezone';
import { fmtSev } from '../formatEvent';

const TYP_TAG =
  'inline-block px-1.5 py-0 rounded border border-border bg-muted text-foreground font-medium';

interface Props {
  events: readonly DispatcherEvent[];
  selected: DispatcherEvent | null;
  onRowClick: (event: DispatcherEvent) => void;
}

export function DispatcherTable({ events, selected, onRowClick }: Props) {
  const { t } = useTranslation();
  const rows = events;

  const th = 'px-3 py-1.5 text-left text-xs font-medium text-muted-foreground whitespace-nowrap';
  const td = 'px-3 py-1.5 text-xs font-mono whitespace-nowrap';

  return (
    <div className="overflow-auto">
      <table className="w-full border-collapse">
        <thead className="border-b border-border bg-muted sticky top-0">
          <tr>
            <th className={th}>id</th>
            <th className={th}>ts</th>
            <th className={th}>src</th>
            <th className={th}>sys</th>
            <th className={th}>typ</th>
            <th className={th}>cmd</th>
            <th className={th}>sev</th>
            <th className={th}>dst</th>
            <th className={`${th} w-full`}>data</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 && (
            <tr>
              <td colSpan={9} className="px-3 py-8 text-center text-xs text-muted-foreground">
                {t('dispatcher.noEvents')}
              </td>
            </tr>
          )}
          {rows.map((e, idx) => {
            const sev = fmtSev(e.sev);
            const dataStr = Object.keys(e.data).length
              ? JSON.stringify(e.data).slice(0, 80)
              : '';
            const isSelected = selected?.id === e.id;
            const rowClass = [
              'cursor-pointer transition-colors border-b border-border',
              isSelected
                ? 'bg-blue-100 hover:bg-blue-100'
                : idx % 2 === 0
                ? 'bg-card hover:bg-muted'
                : 'bg-muted hover:bg-muted-hover',
            ].join(' ');

            return (
              <tr key={e.id} className={rowClass} onClick={() => onRowClick(e)}>
                <td className={`${td} text-muted-foreground`} title={e.id}>
                  {e.id}
                </td>
                <TimestampCell ts={e.ts} timezone={DEFAULT_TIMEZONE} className={`${td} text-muted-foreground`} />
                <td className={td}>{e.src ?? ''}</td>
                <td className={`${td} text-foreground`}>{e.sys || ''}</td>
                <td className={td}>
                  {e.typ ? <span className={TYP_TAG}>{e.typ}</span> : ''}
                </td>
                <td className={td}>{e.cmd ?? ''}</td>
                <td className={`${td} ${sev.cls}`}>{sev.label}</td>
                <td className={`${td} text-muted-foreground`}>{e.dst ?? ''}</td>
                <td
                  className={`${td} text-muted-foreground max-w-xs overflow-hidden text-ellipsis`}
                  title={JSON.stringify(e.data)}
                >
                  {dataStr}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
