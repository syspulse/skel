import React from 'react';
import { IconLamp, IconGrid, IconSettings, IconHelp } from './Icons';

export type NavPage = 'explain' | 'dash' | 'settings' | 'help';

interface SideNavProps {
  activePage: NavPage;
  onNavigate: (page: NavPage) => void;
}

interface NavItem {
  id: NavPage;
  label: string;
  Icon: React.FC<{ size?: number; className?: string }>;
}

const navItems: NavItem[] = [
  { id: 'explain',  label: 'Explain',   Icon: IconLamp     },
  { id: 'dash',     label: 'Dash',      Icon: IconGrid     },
  { id: 'settings', label: 'Settings',  Icon: IconSettings },
  { id: 'help',     label: 'Help',      Icon: IconHelp     },
];

export function SideNav({ activePage, onNavigate }: SideNavProps) {
  return (
    <nav className="fixed top-12 left-0 w-44 bottom-0 bg-nav text-nav-fg flex flex-col pt-1 z-40 border-r border-border shadow-sm">
      {navItems.map(({ id, label, Icon }) => (
        <button
          key={id}
          onClick={() => onNavigate(id)}
          className={`flex items-center gap-2 px-3 py-1.5 text-sm transition-colors text-left w-full
            ${
              activePage === id
                ? 'bg-nav-active border-l-4 border-blue-400 text-nav-fg'
                : 'hover:bg-nav-active border-l-4 border-transparent text-nav-fg-muted hover:text-nav-fg'
            }`}
        >
          <Icon size={18} />
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
}
