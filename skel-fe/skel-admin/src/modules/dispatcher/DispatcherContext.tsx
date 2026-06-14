import React, { createContext, useContext, useEffect, useState } from 'react';
import { dispatcher } from './Dispatcher';
import { DispatcherWs } from './DispatcherWs';
import type { DispatcherEvent, DispatcherStats } from './types';

function resolveWsUrl(): string {
  return (
    localStorage.getItem('VITE_DISPATCHER_WS_URL') ||
    import.meta.env.VITE_DISPATCHER_WS_URL ||
    ''
  );
}

interface DispatcherCtx {
  history:      readonly DispatcherEvent[];
  stats:        DispatcherStats;
  connected:    boolean;
  wsUrl:            string;
  messageCount:     number;
  reconnectAttempts: number;
}

const Ctx = createContext<DispatcherCtx>({
  history:      [],
  stats:        { total: 0, byTyp: {}, bySys: {} },
  connected:    false,
  wsUrl:        '',
  messageCount: 0,
  reconnectAttempts: 0,
});

export function useDispatcher(): DispatcherCtx {
  return useContext(Ctx);
}

export function DispatcherProvider({ children }: { children: React.ReactNode }) {
  const [history, setHistory] = useState<readonly DispatcherEvent[]>(
    () => dispatcher.getHistory(),
  );
  const [stats, setStats] = useState<DispatcherStats>(() => dispatcher.getStats());
  const [connected, setConnected] = useState(false);
  const [wsUrl] = useState(resolveWsUrl);
  const [messageCount, setMessageCount] = useState(0);
  const [reconnectAttempts, setReconnectAttempts] = useState(0);

  useEffect(() => {
    return dispatcher.subscribe(() => {
      setHistory(dispatcher.getHistory());
      setStats(dispatcher.getStats());
    });
  }, []);

  useEffect(() => {
    if (!wsUrl) return;

    const ws = new DispatcherWs({
      url:            wsUrl,
      onConnected:    () => {
        setConnected(true);
        setReconnectAttempts(0);
      },
      onDisconnected: () => setConnected(false),
      onMessage:      () => setMessageCount((n) => n + 1),
      onReconnectAttempt: () => setReconnectAttempts((n) => n + 1),
    });
    ws.connect();
    return () => ws.destroy();
  }, [wsUrl]);

  return (
    <Ctx.Provider value={{ history, stats, connected, wsUrl, messageCount, reconnectAttempts }}>
      {children}
    </Ctx.Provider>
  );
}
