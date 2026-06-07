import React, { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/useAuth';
import { useApp } from '../theme/AppContext';
import { useNotifications } from '../notifications/NotificationContext';
import { NotificationPanel } from '../notifications/NotificationPanel';
import { UserAvatar } from '../auth/UserAvatar';
import { AppBrandMark } from './AppBrand';
import { IconUser, IconLogout, IconBell } from './Icons';

export function TopBar() {
  const { t } = useTranslation();
  const { user, isAuthenticated, logout } = useAuth();
  const { appName, logoUrl } = useApp();
  const { notifications, unreadCount, markAllRead, clearAll } = useNotifications();
  const [panelOpen, setPanelOpen] = useState(false);

  const handleBellClick = () => {
    if (!panelOpen) markAllRead();
    setPanelOpen(v => !v);
  };

  return (
    <>
      <header className="fixed top-0 left-0 right-0 h-12 bg-header text-header-fg flex items-center px-3 z-50 shadow-sm border-b border-border">
        <div className="w-44 flex-shrink-0 min-w-0">
          <AppBrandMark
            appName={appName}
            logoUrl={logoUrl}
            size={22}
            nameClassName="text-lg tracking-wide text-header-fg"
            iconClassName="text-header-fg"
          />
        </div>

        <div className="flex-1" />

        <div className="flex items-center gap-3">
          <button
            onClick={handleBellClick}
            className="relative text-header-fg-muted hover:text-header-fg p-1 rounded transition-colors"
            aria-label={t('notifications.title')}
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
                <UserAvatar
                  avatarUrl={user.avatarUrl}
                  name={user.name}
                  size={24}
                  className="text-header-fg-muted"
                />
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
                {t('topbar.logout')}
              </button>
            </>
          ) : (
            <div className="flex items-center gap-2 text-sm text-header-fg-muted">
              <IconUser size={16} />
              <span>{t('topbar.notLoggedIn')}</span>
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
