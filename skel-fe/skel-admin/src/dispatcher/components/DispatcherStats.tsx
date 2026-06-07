import React from 'react';
import { useTranslation } from 'react-i18next';

interface Props {
  wsUrl:             string;
  connected:         boolean;
  messageCount:      number;
  reconnectAttempts: number;
}

export function DispatcherStats({ wsUrl, connected, messageCount, reconnectAttempts }: Props) {
  const { t } = useTranslation();

  return (
    <div className="flex flex-wrap items-center gap-4 px-4 py-2 border-b border-border bg-muted shrink-0">
      <div
        className="flex items-center gap-1.5 shrink-0"
        title={connected ? t('dispatcher.wsConnected') : t('dispatcher.wsDisconnected')}
      >
        <span className={`w-2 h-2 rounded-full shrink-0 ${connected ? 'bg-green-500' : 'bg-slate-400'}`} />
        <span className="text-xs font-mono tabular-nums text-muted-foreground">
          {reconnectAttempts}
        </span>
      </div>

      <div className="flex items-center gap-1.5 min-w-0 flex-1">
        <span className="text-xs font-mono text-foreground truncate" title={wsUrl || undefined}>
          {wsUrl || t('dispatcher.wsNoUrl')}
        </span>
      </div>

      <div className="flex items-center gap-1.5 shrink-0">
        <span className="text-xs text-muted-foreground">{t('dispatcher.wsMessages')}</span>
        <span className="text-xs font-mono font-semibold text-foreground tabular-nums">{messageCount}</span>
      </div>
    </div>
  );
}
