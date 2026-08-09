import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/useAuth';
import { useApp } from '../theme/AppContext';
import { useNotifications } from '../notifications/NotificationContext';
import { NotificationPanel } from '../notifications/NotificationPanel';
import { UserAvatar } from '../auth/UserAvatar';
import { useAvatarUrl } from '../auth/useAvatarUrl';
import { AppBrandMark } from './AppBrand';
import { IconUser, IconLogout, IconBell, IconInfo, IconSettings } from './Icons';

interface TopBarProps {
  onOpenSettingsTab?: (tab: string) => void;
}

export function TopBar({ onOpenSettingsTab }: TopBarProps) {
  const { t } = useTranslation();
  const { user, isAuthenticated, logout } = useAuth();
  const avatarUrl = useAvatarUrl();
  const { appName, logoUrl } = useApp();
  const { notifications, unreadCount, markAllRead, clearAll } = useNotifications();
  const [panelOpen, setPanelOpen] = useState(false);
  const [userMenuOpen, setUserMenuOpen] = useState(false);
  const userMenuRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!userMenuOpen) return;
    const handler = (e: MouseEvent) => {
      if (userMenuRef.current && !userMenuRef.current.contains(e.target as Node)) {
        setUserMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [userMenuOpen]);

  const handleBellClick = () => {
    if (!panelOpen) markAllRead();
    setPanelOpen(v => !v);
  };

  const openSettingsTab = (tab: string) => {
    setUserMenuOpen(false);
    onOpenSettingsTab?.(tab);
  };

  return (
    <>
      <header className="fixed top-0 left-0 right-0 h-12 bg-header text-header-fg flex items-center px-3 z-50 shadow-sm border-b border-border">
        <div className="w-56 flex-shrink-0 min-w-0">
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
              <div ref={userMenuRef} className="relative">
                <button
                  type="button"
                  onClick={() => setUserMenuOpen(o => !o)}
                  className="text-sm text-header-fg-muted hover:text-header-fg p-1 rounded transition-colors"
                  aria-label={t('topbar.userMenu')}
                  aria-expanded={userMenuOpen}
                  aria-haspopup="menu"
                >
                  <UserAvatar
                    avatarUrl={avatarUrl}
                    name={user.name}
                    size={24}
                    className="text-header-fg-muted"
                  />
                </button>

                {userMenuOpen && (
                  <div
                    role="menu"
                    className="absolute right-0 top-full mt-1 min-w-[10rem] popover py-1 z-[60]"
                  >
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => openSettingsTab('userProfile')}
                      className="menu-item"
                    >
                      <IconInfo size={14} className="text-muted-foreground shrink-0" />
                      {t('topbar.menu.info')}
                    </button>
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => openSettingsTab('profile')}
                      className="menu-item"
                    >
                      <IconSettings size={14} className="text-muted-foreground shrink-0" />
                      {t('topbar.menu.profile')}
                    </button>
                    <hr className="border-border my-1" />
                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => {
                        setUserMenuOpen(false);
                        logout();
                      }}
                      className="menu-item"
                    >
                      <IconLogout size={14} className="text-muted-foreground shrink-0" />
                      {t('topbar.menu.logout')}
                    </button>
                  </div>
                )}
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
