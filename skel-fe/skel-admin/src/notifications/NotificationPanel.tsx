import React from 'react';
import { useTranslation } from 'react-i18next';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Notification, Severity } from './types';
import { NotificationSrcLabel, formatNotificationTs } from './NotificationSrcLabel';
import { IconClose, IconAlertCircle, IconAlertTriangle, IconInfo, IconCheckCircle } from '../components/Icons';

function severityIcon(severity: Severity) {
  switch (severity) {
    case 'error':   return <IconAlertCircle size={14} />;
    case 'warning': return <IconAlertTriangle size={14} />;
    case 'success': return <IconCheckCircle size={14} />;
    default:        return <IconInfo size={14} />;
  }
}

function severityIconColor(severity: Severity) {
  switch (severity) {
    case 'error':   return 'text-red-500';
    case 'warning': return 'text-yellow-500';
    case 'success': return 'text-green-500';
    default:        return 'text-blue-500';
  }
}

interface NotificationPanelProps {
  open: boolean;
  notifications: Notification[];
  onClose: () => void;
  onClearAll: () => void;
}

function NotificationItem({ n }: { n: Notification }) {
  return (
    <div className={`px-3 py-2.5 border-b border-border last:border-0 ${n.read ? '' : 'bg-muted/40'}`}>
      <div className="flex items-start gap-2">
        <span className={`shrink-0 mt-0.5 ${severityIconColor(n.severity)}`}>{severityIcon(n.severity)}</span>
        <div className="flex-1 min-w-0">
          <div className="flex items-baseline justify-between gap-2">
            <span className="text-xs font-medium text-foreground leading-snug">{n.title}</span>
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
      </div>
    </div>
  );
}

export function NotificationPanel({ open, notifications, onClose, onClearAll }: NotificationPanelProps) {
  const { t } = useTranslation();
  return (
    <>
      {open && <div className="fixed inset-0 z-[48]" onClick={onClose} />}

      <div
        className={`fixed top-12 right-0 bottom-0 w-80 max-w-[92vw] bg-card border-l border-border z-[49] flex flex-col
          transition-transform duration-300 ease-in-out pointer-events-none
          ${open ? 'translate-x-0 shadow-2xl pointer-events-auto' : 'translate-x-full shadow-none'}`}
      >
        <div className="flex items-center justify-between px-3 py-2.5 border-b border-border bg-muted shrink-0">
          <h2 className="text-sm text-foreground">{t('notifications.title')}</h2>
          <div className="flex items-center gap-1">
            {notifications.length > 0 && (
              <button
                onClick={onClearAll}
                className="text-xs text-muted-foreground hover:text-foreground px-2 py-0.5 rounded transition-colors"
              >
                {t('notifications.clearAll')}
              </button>
            )}
            <button
              onClick={onClose}
              className="text-muted-foreground hover:text-foreground p-1 rounded transition-colors"
              aria-label={t('common.close')}
            >
              <IconClose size={16} />
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto">
          {notifications.length === 0 ? (
            <p className="text-xs text-muted-foreground text-center mt-12">{t('notifications.empty')}</p>
          ) : (
            notifications.map(n => <NotificationItem key={n.id} n={n} />)
          )}
        </div>
      </div>
    </>
  );
}
