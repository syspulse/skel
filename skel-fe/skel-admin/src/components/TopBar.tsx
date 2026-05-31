import React from 'react';
import { useAuth } from '../auth/useAuth';
import { useApp } from '../theme/AppContext';
import { IconLamp, IconUser, IconLogout } from './Icons';

function AppLogo({ logoUrl, size }: { logoUrl: string; size: number }) {
  if (!logoUrl) return <IconLamp size={size} />;
  const s = logoUrl.trim();
  if (s.toLowerCase().startsWith('<svg')) {
    return (
      <span
        className="inline-flex items-center justify-center shrink-0"
        style={{ width: size, height: size }}
        dangerouslySetInnerHTML={{ __html: s }}
      />
    );
  }
  return <img src={s} alt="logo" width={size} height={size} className="object-contain shrink-0" />;
}

export function TopBar() {
  const { user, isAuthenticated, logout } = useAuth();
  const { appName, logoUrl } = useApp();

  return (
    <header className="fixed top-0 left-0 right-0 h-14 bg-header text-header-fg flex items-center px-4 z-50 shadow-sm border-b border-border">
      <div className="flex items-center gap-2.5 w-52 flex-shrink-0">
        <AppLogo logoUrl={logoUrl} size={22} />
        <span className="text-lg tracking-wide">{appName}</span>
      </div>

      <div className="flex-1" />

      <div className="flex items-center gap-3">
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
  );
}
