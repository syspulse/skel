import React from 'react';
import { useTranslation } from 'react-i18next';
import { PAGE_SIZE_ALL, PAGE_SIZE_OPTIONS } from '../settings/PageSizeContext';

interface PaginationProps {
  page: number;
  pageSize: number;
  total: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  /** Count, loading, errors — shown on the left of the panel. */
  footerLeft?: React.ReactNode;
}

function pageNumbers(current: number, total: number): (number | '...')[] {
  if (total <= 1) return total === 1 ? [1] : [];
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);

  const set = new Set<number>();
  set.add(1);
  set.add(total);
  for (let i = Math.max(1, current - 2); i <= Math.min(total, current + 2); i++) set.add(i);

  const sorted = Array.from(set).sort((a, b) => a - b);
  const result: (number | '...')[] = [];
  for (let i = 0; i < sorted.length; i++) {
    if (i > 0 && sorted[i] - sorted[i - 1] > 1) result.push('...');
    result.push(sorted[i]);
  }
  return result;
}

export function Pagination({
  page,
  pageSize,
  total,
  onPageChange,
  onPageSizeChange,
  footerLeft,
}: PaginationProps) {
  const { t } = useTranslation();
  const totalPages = pageSize === PAGE_SIZE_ALL ? 1 : Math.max(1, Math.ceil(total / pageSize));
  const pages = pageNumbers(page, totalPages);

  const btnBase = 'min-w-[28px] h-7 px-1.5 text-xs rounded border transition-colors flex items-center justify-center';
  const btnActive = 'bg-blue-500 border-blue-500 text-white font-medium';
  const btnNormal = 'border-border text-muted-foreground hover:border-blue-400 hover:text-foreground bg-card';
  const btnDisabled = 'border-border text-muted-foreground/40 bg-card cursor-not-allowed';

  return (
    <div className="flex items-center gap-3 px-4 py-2 border-t border-border bg-muted select-none shrink-0">
      <div className="flex items-center gap-3 min-w-0 flex-1 text-xs text-muted-foreground">
        {footerLeft}
      </div>

      <div className="flex items-center gap-1.5 shrink-0">
        <button
          className={`${btnBase} ${page <= 1 ? btnDisabled : btnNormal}`}
          onClick={() => page > 1 && onPageChange(page - 1)}
          disabled={page <= 1}
          aria-label={t('pagination.prev')}
        >
          ‹
        </button>

        {pages.map((p, i) =>
          p === '...' ? (
            <span key={`ellipsis-${i}`} className="text-xs text-muted-foreground px-0.5">…</span>
          ) : (
            <button
              key={p}
              className={`${btnBase} ${p === page ? btnActive : btnNormal}`}
              onClick={() => onPageChange(p)}
            >
              {p}
            </button>
          )
        )}

        <button
          className={`${btnBase} ${page >= totalPages ? btnDisabled : btnNormal}`}
          onClick={() => page < totalPages && onPageChange(page + 1)}
          disabled={page >= totalPages}
          aria-label={t('pagination.next')}
        >
          ›
        </button>

        <select
          value={pageSize}
          onChange={(e) => onPageSizeChange(Number(e.target.value))}
          className="text-xs border border-input rounded px-1.5 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 cursor-pointer ml-2"
        >
          {PAGE_SIZE_OPTIONS.map((s) => (
            <option key={s} value={s}>
              {s === PAGE_SIZE_ALL ? t('pagination.all') : `${s} ${t('pagination.perPage')}`}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}
