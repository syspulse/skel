export type TimeRange =
  | { type: 'all' }
  | { type: 'last'; hours: number }
  | { type: 'custom'; start: Date; end: Date };
