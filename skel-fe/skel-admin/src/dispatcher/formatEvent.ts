export function fmtSev(sev?: number): { label: string; cls: string } {
  if (sev === undefined) return { label: '', cls: 'text-muted-foreground' };
  if (sev <= 0.0) return { label: String(sev), cls: 'text-green-600' };
  if (sev <= 0.15) return { label: String(sev), cls: 'text-blue-500' };
  if (sev <= 0.45) return { label: String(sev), cls: 'text-yellow-600' };
  return { label: String(sev), cls: 'text-red-500' };
}
