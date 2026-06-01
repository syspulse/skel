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

const TOP_ITEMS: NavItem[] = [
  { id: 'explain', labelKey: 'nav.explain', Icon: IconLamp },
  { id: 'dash',    labelKey: 'nav.dash',    Icon: IconGrid },
];

const BOTTOM_ITEMS: NavItem[] = [
  { id: 'settings', labelKey: 'nav.settings', Icon: IconSettings },
  { id: 'help',     labelKey: 'nav.help',     Icon: IconHelp     },
];

function NavButton({ item, active, onNavigate }: { item: NavItem; active: boolean; onNavigate: (page: NavPage) => void }) {
  const { t } = useTranslation();
  return (
    <button
      onClick={() => onNavigate(item.id)}
      className={`flex items-center gap-2 px-3 py-1.5 text-sm transition-colors text-left w-full
        ${active
          ? 'bg-nav-active border-l-4 border-blue-400 text-nav-fg'
          : 'hover:bg-nav-active border-l-4 border-transparent text-nav-fg-muted hover:text-nav-fg'
        }`}
    >
      <item.Icon size={18} />
      <span>{t(item.labelKey)}</span>
    </button>
  );
}

export function SideNav({ activePage, onNavigate }: SideNavProps) {
  return (
    <nav className="fixed top-12 left-0 w-44 bottom-0 bg-nav text-nav-fg flex flex-col pt-1 z-40 border-r border-border shadow-sm">
      <div className="flex-1">
        {TOP_ITEMS.map((item) => (
          <NavButton key={item.id} item={item} active={activePage === item.id} onNavigate={onNavigate} />
        ))}
      </div>
      <div className="border-t border-border">
        {BOTTOM_ITEMS.map((item) => (
          <NavButton key={item.id} item={item} active={activePage === item.id} onNavigate={onNavigate} />
        ))}
      </div>
    </nav>
  );
}
