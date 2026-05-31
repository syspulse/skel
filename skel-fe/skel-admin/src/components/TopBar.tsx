import React, { useState } from 'react';
import { useAuth } from '../auth/useAuth';
import { useNotifications } from '../notifications/NotificationContext';
import { NotificationPanel } from '../notifications/NotificationPanel';
import { IconLamp, IconUser, IconLogout, IconBell } from './Icons';

export function TopBar() {
  const { user, isAuthenticated, logout } = useAuth();
  const { notifications, unreadCount, markAllRead, clearAll } = useNotifications();
  const [panelOpen, setPanelOpen] = useState(false);

  const handleBellClick = () => {
    if (!panelOpen) markAllRead();
    setPanelOpen(v => !v);
  };

  return (
    <>
      <header className="fixed top-0 left-0 right-0 h-12 bg-header text-header-fg flex items-center px-3 z-50 shadow-sm border-b border-border">
        <div className="flex items-center gap-2 w-44 flex-shrink-0">
          <IconLamp size={22} />
          <span className="text-lg tracking-wide">admin</span>
        </div>

        <div className="flex-1" />

        <div className="flex items-center gap-3">
          <button
            onClick={handleBellClick}
            className="relative text-header-fg-muted hover:text-header-fg p-1 rounded transition-colors"
            aria-label="Notifications"
          >
            <IconBell size={18} />
            {unreadCount > 0 && (
              <span className="absolute -top-0.5 -right-0.5 min-w-[16px] h-4 px-0.5 bg-red-500 text-white text-[10px] font-bold rounded-full flex items-center justify-center leading-none">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </button>

          {isAuthenticated && user ? (
            <>
              <div className="flex items-center gap-2 text-sm text-header-fg-muted">
                <IconUser size={16} />
                <span>
                  {user.name}
                  {user.email ? (
                    <span className="text-header-fg-muted ml-1 text-xs">({user.email})</span>
                  ) : null}
                </span>
              </div>
              <button
                onClick={logout}
                className="inline-flex items-center gap-1.5 text-xs bg-nav hover:bg-nav-active px-3 py-1 rounded transition-colors"
              >
                <IconLogout size={14} />
                Logout
              </button>
            </>
          ) : (
            <div className="flex items-center gap-2 text-sm text-header-fg-muted">
              <IconUser size={16} />
              <span>Not logged in</span>
            </div>
          )}
        </div>
      </header>

      <NotificationPanel
        open={panelOpen}
        notifications={notifications}
        onClose={() => setPanelOpen(false)}
        onClearAll={clearAll}
      />
    </>
  );
}
