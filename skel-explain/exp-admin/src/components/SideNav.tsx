import React from 'react';
import { IconLamp, IconSettings, IconHelp } from './Icons';

export type NavPage = 'explain' | 'settings' | 'help';

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
  { id: 'explain',  label: 'Explain',  Icon: IconLamp     },
  { id: 'settings', label: 'Settings', Icon: IconSettings },
  { id: 'help',     label: 'Help',     Icon: IconHelp     },
];

export function SideNav({ activePage, onNavigate }: SideNavProps) {
  return (
    <nav className="fixed top-14 left-0 w-52 bottom-0 bg-slate-700 text-white flex flex-col pt-4 z-40 shadow-lg">
      {navItems.map(({ id, label, Icon }) => (
        <button
          key={id}
          onClick={() => onNavigate(id)}
          className={`flex items-center gap-3 px-5 py-3 text-sm font-medium transition-colors text-left w-full
            ${
              activePage === id
                ? 'bg-slate-600 border-l-4 border-blue-400 text-white'
                : 'hover:bg-slate-600 border-l-4 border-transparent text-slate-300 hover:text-white'
            }`}
        >
          <Icon size={18} />
          <span>{label}</span>
        </button>
      ))}
    </nav>
  );
}
