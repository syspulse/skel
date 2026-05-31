import React from 'react';
import type { Explain } from '../types';
import { IconLamp } from './Icons';

function RuleIcon({ meta }: { meta?: Record<string, unknown> | null }) {
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

function formatTs(ts: number, utc: boolean): string {
  const d = new Date(ts);
  const day = utc ? d.getUTCDate()    : d.getDate();
  const mon = MONTHS[utc ? d.getUTCMonth()   : d.getMonth()];
  const hh  = String(utc ? d.getUTCHours()   : d.getHours()).padStart(2, '0');
  const mm  = String(utc ? d.getUTCMinutes() : d.getMinutes()).padStart(2, '0');
  const ss  = String(utc ? d.getUTCSeconds() : d.getSeconds()).padStart(2, '0');
  return `${day} ${mon} ${hh}:${mm}:${ss}`;
}

function rowKey(rule: Explain): string {
  return `${rule.oid ?? ''}_${rule.rid}`;
}

interface ExplainTableProps {
  rules: Explain[];
  selected: Explain | null;
  selectedIds: Set<string>;
  utc: boolean;
  onRowClick: (rule: Explain) => void;
  onCheckboxChange: (rule: Explain, checked: boolean) => void;
  onSelectAll: (checked: boolean) => void;
}

export function ExplainTable({
  rules,
  selected,
  selectedIds,
  utc,
  onRowClick,
  onCheckboxChange,
  onSelectAll,
}: ExplainTableProps) {
  const allChecked = rules.length > 0 && rules.every((r) => selectedIds.has(rowKey(r)));
  const someChecked = rules.some((r) => selectedIds.has(rowKey(r)));

  if (rules.length === 0) {
    return (
      <div className="flex items-center justify-center py-16 text-muted-foreground text-sm">
        No rules found.
      </div>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-sm">
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
            <th className="w-10 px-2 py-2 text-center">icon</th>
            <th className="w-32 px-3 py-2 text-left whitespace-nowrap">ts0{utc ? ' (UTC)' : ''}</th>
            <th className="px-3 py-2 text-left">oid</th>
            <th className="px-3 py-2 text-left">rid</th>
            <th className="px-3 py-2 text-left">name</th>
            <th className="px-3 py-2 text-left">desc</th>
          </tr>
        </thead>
        <tbody>
          {rules.map((rule, idx) => {
            const key = rowKey(rule);
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
              <tr
                key={key}
                className={rowClass}
                onClick={() => onRowClick(rule)}
              >
                <td
                  className="px-3 py-2 text-center"
                  onClick={(e) => e.stopPropagation()}
                >
                  <input
                    type="checkbox"
                    checked={isChecked}
                    onChange={(e) => onCheckboxChange(rule, e.target.checked)}
                    className="cursor-pointer"
                  />
                </td>
                <td className="px-2 py-2 text-center">
                  <span className="inline-flex items-center justify-center">
                    <RuleIcon meta={rule.meta as Record<string, unknown> | undefined} />
                  </span>
                </td>
                <td className="px-3 py-2 whitespace-nowrap font-mono text-xs text-muted-foreground">
                  {formatTs(rule.ts0, utc)}
                </td>
                <td className="px-3 py-2 font-mono text-xs text-muted-foreground max-w-[140px] truncate">
                  {rule.oid || <span className="opacity-30">—</span>}
                </td>
                <td className="px-3 py-2 font-mono text-xs text-foreground max-w-[180px] truncate">
                  {rule.rid}
                </td>
                <td className="px-3 py-2 text-foreground max-w-[160px] truncate">
                  {rule.name || <span className="opacity-30">—</span>}
                </td>
                <td className="px-3 py-2 text-muted-foreground max-w-[240px] truncate">
                  {rule.desc || <span className="opacity-30">—</span>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
