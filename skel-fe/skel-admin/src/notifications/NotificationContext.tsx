import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Notification, NotificationInput, Severity } from './types';
import { LOCAL_NOTIFICATION_SRC } from './types';
import { NotificationSrcLabel, formatNotificationTs } from './NotificationSrcLabel';
import { IconClose, IconAlertCircle, IconAlertTriangle, IconInfo, IconCheckCircle } from '../components/Icons';
import { dispatcher } from '../modules/dispatcher/Dispatcher';
import { createNotificationSys } from '../modules/dispatcher/systems/NotificationSys';

const TOAST_DURATION = 5000;

interface NotificationCtx {
  notifications: Notification[];
  unreadCount:   number;
  add:  (severity: Severity, title: string, message: string, src?: string) => void;
  push: (input: NotificationInput) => void;
  markAllRead: () => void;
  clearAll:    () => void;
}

const Ctx = createContext<NotificationCtx | null>(null);

export function useNotifications(): NotificationCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error('useNotifications must be used inside NotificationProvider');
  return ctx;
}

function severityIcon(severity: Severity) {
  switch (severity) {
    case 'error':   return <IconAlertCircle size={16} />;
    case 'warning': return <IconAlertTriangle size={16} />;
    case 'success': return <IconCheckCircle size={16} />;
    default:        return <IconInfo size={16} />;
  }
}

function severityColors(severity: Severity) {
  switch (severity) {
    case 'error':   return { border: 'border-red-500',    icon: 'text-red-500',    bg: 'bg-red-50 dark:bg-red-950/30' };
    case 'warning': return { border: 'border-yellow-500', icon: 'text-yellow-500', bg: 'bg-yellow-50 dark:bg-yellow-950/30' };
    case 'success': return { border: 'border-green-500',  icon: 'text-green-500',  bg: 'bg-green-50 dark:bg-green-950/30' };
    default:        return { border: 'border-blue-500',   icon: 'text-blue-500',   bg: 'bg-blue-50 dark:bg-blue-950/30' };
  }
}

function severityToNum(severity: Severity): number {
  switch (severity) {
    case 'success': return 0.0;
    case 'info':    return 0.1;
    case 'warning': return 0.3;
    case 'error':   return 0.5;
  }
}

interface ToastItem {
  id:           string;
  notification: Notification;
  visible:      boolean;
}

function Toast({ item, onDismiss }: { item: ToastItem; onDismiss: (id: string) => void }) {
  const n = item.notification;
  const c = severityColors(n.severity);
  return (
    <div
      className={`flex gap-2 w-80 max-w-[92vw] bg-card border border-l-4 ${c.border} rounded shadow-lg px-3 py-2.5
        transition-all duration-300 ${item.visible ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2'}`}
    >
      <span className={`shrink-0 mt-0.5 ${c.icon}`}>{severityIcon(n.severity)}</span>
      <div className="flex-1 min-w-0">
        <div className="flex items-start justify-between gap-2">
          <div className="text-xs font-medium text-foreground leading-snug">{n.title}</div>
          <span className="flex items-center gap-1.5 shrink-0">
            <NotificationSrcLabel src={n.src} />
            <span className="text-[10px] text-muted-foreground">{formatNotificationTs(n.ts)}</span>
          </span>
        </div>
        {n.message && (
          <div className="text-xs text-muted-foreground mt-0.5 prose prose-xs max-w-none [&>*:first-child]:mt-0 [&>*:last-child]:mb-0">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{n.message}</ReactMarkdown>
          </div>
        )}
      </div>
      <button
        onClick={() => onDismiss(item.id)}
        className="shrink-0 text-muted-foreground hover:text-foreground transition-colors mt-0.5"
        aria-label="Dismiss"
      >
        <IconClose size={14} />
      </button>
    </div>
  );
}

let _idCounter = 0;
function nextId() { return `n-${Date.now()}-${++_idCounter}`; }

function createNotification(input: NotificationInput, id = nextId()): Notification {
  return {
    id,
    severity: input.severity,
    title:    input.title,
    message:  input.message,
    src:      input.src.trim() || LOCAL_NOTIFICATION_SRC,
    ts:       input.ts ?? Date.now(),
    read:     false,
  };
}

export function NotificationProvider({ children }: { children: React.ReactNode }) {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const timersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map());

  const dismissToast = useCallback((id: string) => {
    setToasts(prev => prev.map(t => t.id === id ? { ...t, visible: false } : t));
    const existing = timersRef.current.get(id);
    if (existing) clearTimeout(existing);
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 300);
  }, []);

  const showToast = useCallback((notification: Notification) => {
    const toast: ToastItem = { id: notification.id, notification, visible: false };
    setToasts(prev => [...prev, toast]);
    requestAnimationFrame(() => {
      setToasts(prev => prev.map(t => t.id === notification.id ? { ...t, visible: true } : t));
    });
    const timer = setTimeout(() => dismissToast(notification.id), TOAST_DURATION);
    timersRef.current.set(notification.id, timer);
  }, [dismissToast]);

  // Internal: add directly to state — called by NotificationSys handler (avoids dispatch loop)
  const addDirect = useCallback((input: NotificationInput) => {
    const notification = createNotification(input);
    setNotifications(prev => [notification, ...prev]);
    showToast(notification);
  }, [showToast]);

  // Register NotificationSys with dispatcher for sys="" (default) and sys="NotificationSys"
  useEffect(() => {
    const handler = createNotificationSys(addDirect);
    const u1 = dispatcher.register('', handler);
    const u2 = dispatcher.register('NotificationSys', handler);
    return () => { u1(); u2(); };
  }, [addDirect]);

  // Public push — routes through dispatcher so every local notification appears in history
  const push = useCallback((input: NotificationInput) => {
    dispatcher.dispatch({
      id:   nextId(),
      ts:   input.ts ?? Date.now(),
      sys:  '',
      src:  input.src || LOCAL_NOTIFICATION_SRC,
      typ:  'NOTIFY',
      cmd:  'notify',
      sev:  severityToNum(input.severity),
      data: {
        severity: input.severity,
        title:    input.title,
        message:  input.message,
      },
    });
  }, []);

  const add = useCallback((
    severity: Severity,
    title: string,
    message: string,
    src = LOCAL_NOTIFICATION_SRC,
  ) => {
    push({ severity, title, message, src });
  }, [push]);

  const markAllRead = useCallback(() => {
    setNotifications(prev => prev.map(n => ({ ...n, read: true })));
  }, []);

  const clearAll = useCallback(() => {
    setNotifications([]);
  }, []);

  useEffect(() => {
    return () => { timersRef.current.forEach(t => clearTimeout(t)); };
  }, []);

  const unreadCount = notifications.filter(n => !n.read).length;

  return (
    <Ctx.Provider value={{ notifications, unreadCount, add, push, markAllRead, clearAll }}>
      {children}
      <div className="fixed bottom-4 right-4 z-[100] flex flex-col gap-2 items-end pointer-events-none">
        {toasts.map(item => (
          <div key={item.id} className="pointer-events-auto">
            <Toast item={item} onDismiss={dismissToast} />
          </div>
        ))}
      </div>
    </Ctx.Provider>
  );
}
