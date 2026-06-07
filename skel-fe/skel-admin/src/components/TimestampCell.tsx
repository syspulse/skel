import React from 'react';
import { FormattedTimestamp } from './FormattedTimestamp';

interface TimestampCellProps {
  ts: number;
  timezone: string;
  className?: string;
}

export function TimestampCell({
  ts,
  timezone,
  className = 'px-3 py-2 whitespace-nowrap text-xs text-muted-foreground',
}: TimestampCellProps) {
  return (
    <td className={className}>
      <FormattedTimestamp ts={ts} timezone={timezone} />
    </td>
  );
}
