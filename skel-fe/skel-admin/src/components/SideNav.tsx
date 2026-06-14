import React, { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { IconLamp, IconGrid, IconSettings, IconHelp, IconDispatcher, IconWorkflow } from './Icons';
import { useAuth } from '../auth/useAuth';
import * as wfApi from '../modules/workflow/api';
import { KIND, type WorkflowKind } from '../modules/workflow/types';

export type NavPage = 'explain' | 'dash' | 'dispatcher' | 'workflow' | 'settings' | 'help';

export interface WorkflowInstanceRef { kind: WorkflowKind; id: number; name: string; }
export interface SelectedWorkflowInstance { kind: WorkflowKind; id: number; }

interface SideNavProps {
  activePage: NavPage;
  onNavigate: (page: NavPage) => void;
  /** Open the Workflow module UI (tabs); also exits any open graph editor. */
  onOpenWorkflowHome?: () => void;
  onOpenWorkflowInstance?: (kind: WorkflowKind, id: number) => void;
  selectedWorkflowInstance?: SelectedWorkflowInstance | null;
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

const navBtnClass = (active: boolean): string =>
  `flex items-center gap-2 px-3 py-1.5 text-sm transition-colors text-left w-full
    ${active
      ? 'bg-nav-active border-l-4 border-blue-400 text-nav-fg'
      : 'hover:bg-nav-active border-l-4 border-transparent text-nav-fg-muted hover:text-nav-fg'}`;

function NavButton({ item, active, onNavigate }: { item: NavItem; active: boolean; onNavigate: (page: NavPage) => void }) {
  const { t } = useTranslation();
  return (
    <button onClick={() => onNavigate(item.id)} className={navBtnClass(active)}>
      <item.Icon size={18} />
      <span>{t(item.labelKey)}</span>
    </button>
  );
}

/** Workflow nav item with submenus: one per WorkflowSchema/WorkflowConfig instance.
 *  Submenus are only visible while the Workflow module is the active page. */
function WorkflowNav({ active, onOpenHome, onOpenInstance, selected, refreshKey }: {
  active: boolean;
  onOpenHome?: () => void;
  onOpenInstance?: (kind: WorkflowKind, id: number) => void;
  selected?: SelectedWorkflowInstance | null;
  refreshKey?: number;
}) {
  const { t } = useTranslation();
  const { token } = useAuth();
  const [items, setItems] = useState<WorkflowInstanceRef[]>([]);
  const [collapsed, setCollapsed] = useState(false);

  const load = useCallback(async () => {
    try {
      const [s, c] = await Promise.all([wfApi.listSchemas(token), wfApi.listConfigs(token)]);
      setItems([
        ...(s.schemas ?? []).map((x) => ({ kind: KIND.workflowSchema, id: x.id, name: x.name })),
        ...(c.configs ?? []).map((x) => ({ kind: KIND.workflowConfig, id: x.id, name: x.name })),
      ]);
    } catch {
      setItems([]);
    }
  }, [token]);

  // load on mount / when active toggles / when data changed so the submenu indicator is known
  useEffect(() => { load(); }, [load, refreshKey, active]);

  // toggle submenus when already on the module; otherwise open the module (and show them)
  const handleClick = () => {
    if (active) setCollapsed((c) => !c);
    else setCollapsed(false);
    onOpenHome?.();
  };
  const expanded = active && !collapsed;

  return (
    <div>
      <button onClick={handleClick} className={navBtnClass(active)}>
        <IconWorkflow size={18} />
        <span className="flex-1">{t('nav.workflow')}</span>
        {items.length > 0 && (
          <svg
            width="12" height="12" viewBox="0 0 24 24" fill="none"
            stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"
            className={`opacity-50 shrink-0 transition-transform ${expanded ? 'rotate-180' : ''}`}
            aria-hidden="true"
          >
            <path d="m6 9 6 6 6-6" />
          </svg>
        )}
      </button>

      {/* submenus visible only when the module is active AND not collapsed */}
      {expanded && items.length > 0 && (
        <div className="pb-1">
          {items.map((it) => {
            const isSel = selected?.kind === it.kind && selected?.id === it.id;
            return (
              <button
                key={`${it.kind.replace('workflow-', '')}-${it.id}`}
                onClick={() => onOpenInstance?.(it.kind, it.id)}
                className={`flex items-center gap-1.5 pl-9 pr-3 py-1 text-xs text-left w-full transition-colors
                  ${isSel
                    ? 'bg-nav-active text-nav-fg border-l-4 border-blue-300'
                    : 'text-nav-fg-muted hover:bg-nav-active hover:text-nav-fg border-l-4 border-transparent'}`}
                title={`${it.name} (${it.kind.replace('workflow-', '')})`}
              >
                <span className="truncate flex-1">{it.name}</span>
                <span className={`text-[9px] px-1 py-0.5 rounded shrink-0 ${it.kind === KIND.workflowConfig ? 'bg-emerald-100 text-emerald-700' : 'bg-blue-100 text-blue-700'}`}>
                  {it.id}
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

export function SideNav({ activePage, onNavigate, onOpenWorkflowHome, onOpenWorkflowInstance, selectedWorkflowInstance, workflowRefreshKey }: SideNavProps) {
  return (
    <nav className="fixed top-12 left-0 w-44 bottom-0 bg-nav text-nav-fg flex flex-col pt-1 z-40 border-r border-border shadow-sm overflow-y-auto">
      <div className="flex-1">
        {TOP_ITEMS.map((item) => (
          <NavButton key={item.id} item={item} active={activePage === item.id} onNavigate={onNavigate} />
        ))}
        <WorkflowNav
          active={activePage === 'workflow'}
          onOpenHome={onOpenWorkflowHome}
          onOpenInstance={onOpenWorkflowInstance}
          selected={selectedWorkflowInstance}
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
