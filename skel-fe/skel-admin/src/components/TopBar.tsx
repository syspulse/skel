import React from 'react';
import { useAuth } from '../auth/useAuth';
import { IconLamp, IconUser, IconLogout } from './Icons';

export function TopBar() {
  const { user, isAuthenticated, logout } = useAuth();

  return (
    <header className="fixed top-0 left-0 right-0 h-14 bg-header text-header-fg flex items-center px-4 z-50 shadow-sm border-b border-border">
      <div className="flex items-center gap-2.5 w-52 flex-shrink-0">
        <IconLamp size={22} />
        <span className="text-lg font-semibold tracking-wide">Explain Admin</span>
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
