const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export function formatNotificationTs(ts: number): string {
  const d = new Date(ts);
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

interface NotificationSrcLabelProps {
  src: string;
}

export function NotificationSrcLabel({ src }: NotificationSrcLabelProps) {
  return (
    <span className="text-[10px] font-mono text-muted-foreground px-1 py-0 rounded bg-muted border border-border shrink-0">
      {src}
    </span>
  );
}
