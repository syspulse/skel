import React, { useState } from 'react';

export interface ModuleTab {
  id: string;
  label: string;
}

export const OVERVIEW_TAB = 'overview';

interface ModuleTabsProps {
  tabs: ModuleTab[];
  active: string;
  onChange: (id: string) => void;
}

export function ModuleTabs({ tabs, active, onChange }: ModuleTabsProps) {
  return (
    <div className="flex border-b border-border gap-1">
      {tabs.map(({ id, label }) => (
        <button
          key={id}
          type="button"
          onClick={() => onChange(id)}
          className={`px-3 py-1 text-xs transition-colors border-b-2 -mb-px
            ${active === id
              ? 'border-blue-500 text-foreground'
              : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
        >
          {label}
        </button>
      ))}
    </div>
  );
}

interface ModulePageProps {
  title: string;
  tabs: ModuleTab[];
  defaultTab?: string;
  contentClassName?: string;
  /** When false, tab content spans full width (filters/table modules). Default true. */
  padded?: boolean;
  children: (activeTab: string) => React.ReactNode;
}

export function ModulePage({
  title,
  tabs,
  defaultTab,
  contentClassName = '',
  padded = true,
  children,
}: ModulePageProps) {
  const [tab, setTab] = useState(defaultTab ?? tabs[0]?.id ?? '');

  return (
    <div className="w-full h-full flex flex-col">
      <div className="px-4 pt-3 pb-0 space-y-2 shrink-0">
        <h1 className="text-lg text-foreground">{title}</h1>
        <ModuleTabs tabs={tabs} active={tab} onChange={setTab} />
      </div>
      <div className={`flex-1 min-h-0 ${padded ? 'px-4 pb-3' : ''} ${contentClassName}`.trim()}>
        {children(tab)}
      </div>
    </div>
  );
}
