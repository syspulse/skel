import React from 'react';
import { useTranslation } from 'react-i18next';
import { IconLamp, IconGrid, IconSettings, IconHelp } from './Icons';

export type NavPage = 'explain' | 'dash' | 'settings' | 'help';

interface SideNavProps {
  activePage: NavPage;
  onNavigate: (page: NavPage) => void;
}

interface NavItem {
  id: NavPage;
  labelKey: string;
  Icon: React.FC<{ size?: number; className?: string }>;
}

const NAV_ITEMS: NavItem[] = [
  { id: 'explain',  labelKey: 'nav.explain',  Icon: IconLamp     },
  { id: 'dash',     labelKey: 'nav.dash',     Icon: IconGrid     },
  { id: 'settings', labelKey: 'nav.settings', Icon: IconSettings },
  { id: 'help',     labelKey: 'nav.help',     Icon: IconHelp     },
];

export function SideNav({ activePage, onNavigate }: SideNavProps) {
  const { t } = useTranslation();
  return (
    <nav className="fixed top-12 left-0 w-44 bottom-0 bg-nav text-nav-fg flex flex-col pt-1 z-40 border-r border-border shadow-sm">
      {NAV_ITEMS.map(({ id, labelKey, Icon }) => (
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
          <span>{t(labelKey)}</span>
        </button>
      ))}
    </nav>
  );
}
