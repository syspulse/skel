import React from 'react';
import { formatTimestamp } from './timestamp';
import { useTimestampFormat } from '../settings/TimestampFormatContext';

interface FormattedTimestampProps {
  ts: number;
  timezone: string;
}

export function FormattedTimestamp({ ts, timezone }: FormattedTimestampProps) {
  const { formatPattern } = useTimestampFormat();
  return <>{formatTimestamp(ts, timezone, formatPattern)}</>;
}
