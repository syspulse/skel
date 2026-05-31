import React from 'react';
import { useAuth } from '../auth/useAuth';
import { useApp } from '../theme/AppContext';
import { AppBrandMark } from './AppBrand';
import { IconUser, IconLogout } from './Icons';

export function TopBar() {
  const { user, isAuthenticated, logout } = useAuth();
  const { appName, logoUrl } = useApp();

  return (
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
