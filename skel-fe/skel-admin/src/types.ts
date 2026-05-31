export type TimeRange =
  | { type: 'last'; hours: number }
  | { type: 'custom'; start: Date; end: Date };
