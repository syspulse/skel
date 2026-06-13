import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { IconLamp, IconGrid, IconSettings, IconHelp, IconDispatcher, IconWorkflow } from './Icons';
import { useAuth } from '../auth/useAuth';
import * as wfApi from '../workflow/api';

export type NavPage = 'explain' | 'dash' | 'dispatcher' | 'workflow' | 'settings' | 'help';

export interface WorkflowInstanceRef { kind: 'schema' | 'config'; id: number; name: string; }

interface SideNavProps {
  activePage: NavPage;
  onNavigate: (page: NavPage) => void;
  onOpenWorkflowInstance?: (kind: 'schema' | 'config', id: number) => void;
  workflowRefreshKey?: number;
}

interface NavItem {
  id: NavPage;
  labelKey: string;
  Icon: React.FC<{ size?: number; className?: string }>;
}

const TOP_ITEMS: NavItem[] = [
  { id: 'explain',    labelKey: 'nav.explain',    Icon: IconLamp },
  { id: 'dash',       labelKey: 'nav.dash',       Icon: IconGrid },
  { id: 'dispatcher', labelKey: 'nav.dispatcher', Icon: IconDispatcher },
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

/** Workflow nav item with submenus: one per WorkflowSchema/WorkflowConfig instance. */
function WorkflowNav({ active, onNavigate, onOpenInstance, refreshKey }: {
  active: boolean;
  onNavigate: (page: NavPage) => void;
  onOpenInstance?: (kind: 'schema' | 'config', id: number) => void;
  refreshKey?: number;
}) {
  const { t } = useTranslation();
  const { token } = useAuth();
  const [items, setItems] = useState<WorkflowInstanceRef[]>([]);
  const [expanded, setExpanded] = useState(true);

  const load = useCallback(async () => {
    try {
      const [s, c] = await Promise.all([wfApi.listSchemas(token), wfApi.listConfigs(token)]);
      const refs: WorkflowInstanceRef[] = [
        ...(s.schemas ?? []).map((x) => ({ kind: 'schema' as const, id: x.id, name: x.name })),
        ...(c.configs ?? []).map((x) => ({ kind: 'config' as const, id: x.id, name: x.name })),
      ];
      setItems(refs);
    } catch {
      setItems([]);
    }
  }, [token]);

  useEffect(() => { load(); }, [load, refreshKey]);

  return (
    <div>
      <button
        onClick={() => { onNavigate('workflow'); setExpanded(true); }}
        className={`flex items-center gap-2 px-3 py-1.5 text-sm transition-colors text-left w-full
          ${active
            ? 'bg-nav-active border-l-4 border-blue-400 text-nav-fg'
            : 'hover:bg-nav-active border-l-4 border-transparent text-nav-fg-muted hover:text-nav-fg'}`}
      >
        <IconWorkflow size={18} />
        <span className="flex-1">{t('nav.workflow')}</span>
        {items.length > 0 && (
          <span
            onClick={(e) => { e.stopPropagation(); setExpanded((v) => !v); }}
            className="text-xs text-nav-fg-muted px-1"
          >
            {expanded ? '▾' : '▸'}
          </span>
        )}
      </button>

      {expanded && items.length > 0 && (
        <div className="pb-1">
          {items.map((it) => (
            <button
              key={`${it.kind}-${it.id}`}
              onClick={() => onOpenInstance?.(it.kind, it.id)}
              className="flex items-center gap-1.5 pl-9 pr-3 py-1 text-xs text-left w-full text-nav-fg-muted hover:bg-nav-active hover:text-nav-fg transition-colors"
              title={`${it.name} (${it.kind})`}
            >
              <span className="truncate flex-1">{it.name}</span>
              <span className={`text-[9px] px-1 py-0.5 rounded shrink-0 ${it.kind === 'config' ? 'bg-emerald-100 text-emerald-700' : 'bg-blue-100 text-blue-700'}`}>
                {it.kind}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

export function SideNav({ activePage, onNavigate, onOpenWorkflowInstance, workflowRefreshKey }: SideNavProps) {
  return (
    <nav className="fixed top-12 left-0 w-44 bottom-0 bg-nav text-nav-fg flex flex-col pt-1 z-40 border-r border-border shadow-sm overflow-y-auto">
      <div className="flex-1">
        {TOP_ITEMS.map((item) => (
          <NavButton key={item.id} item={item} active={activePage === item.id} onNavigate={onNavigate} />
        ))}
        <WorkflowNav
          active={activePage === 'workflow'}
          onNavigate={onNavigate}
          onOpenInstance={onOpenWorkflowInstance}
          refreshKey={workflowRefreshKey}
        />
      </div>
      <div className="border-t border-border">
        {BOTTOM_ITEMS.map((item) => (
          <NavButton key={item.id} item={item} active={activePage === item.id} onNavigate={onNavigate} />
        ))}
      </div>
    </nav>
  );
}
