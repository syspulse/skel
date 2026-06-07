import React from 'react';
import { FormattedTimestamp } from './FormattedTimestamp';
import { TABLE_TD } from '../constants/table';

interface TimestampCellProps {
  ts: number;
  timezone: string;
  className?: string;
}

export function TimestampCell({
  ts,
  timezone,
  className = `${TABLE_TD} whitespace-nowrap text-muted-foreground`,
}: TimestampCellProps) {
  return (
    <td className={className}>
      <FormattedTimestamp ts={ts} timezone={timezone} />
    </td>
  );
}
