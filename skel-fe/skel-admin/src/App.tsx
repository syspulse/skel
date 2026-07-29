import React, { useState } from 'react';
import { useAuth } from './auth/useAuth';
import { LoginScreen } from './auth/LoginScreen';
import { useApp } from './theme/AppContext';
import { AppLogo } from './components/AppBrand';
import { SideNav, NavPage } from './components/SideNav';
import { TopBar } from './components/TopBar';
import { ExplainPage } from './modules/explain/ExplainPage';
import { DashPage } from './modules/dash/DashPage';
import { DispatcherPage } from './modules/dispatcher/DispatcherPage';
import { WorkflowPage, type WorkflowEditTarget } from './modules/workflow/WorkflowPage';
import type { WorkflowKind } from './modules/workflow/types';
import { HelpPage } from './modules/help/HelpPage';
import { SettingsPage } from './settings/SettingsPage';

function AppContent() {
  const { isLoading, error, isAuthenticated, loginWithOption, loginWithKeycloak } = useAuth();
  const { appName, logoUrl } = useApp();
  const [activePage, setActivePage] = useState<NavPage>('explain');
  const [settingsTabRequest, setSettingsTabRequest] = useState<string | null>(null);
  const [workflowEditTarget, setWorkflowEditTarget] = useState<WorkflowEditTarget | null>(null);
  const [workflowRefreshKey, setWorkflowRefreshKey] = useState(0);
  const [selectedWorkflowInstance, setSelectedWorkflowInstance] = useState<{ kind: WorkflowKind; id: number } | null>(null);
  const [workflowHomeKey, setWorkflowHomeKey] = useState(0);

  // submenu instance -> open its graph editor and keep it highlighted
  const openWorkflowInstance = (kind: WorkflowKind, id: number) => {
    setWorkflowEditTarget({ kind, id });
    setSelectedWorkflowInstance({ kind, id });
    setActivePage('workflow');
  };

  // main "Workflow" menu -> open the module UI (tabs); exit any open editor; clear submenu selection
  const openWorkflowHome = () => {
    setSelectedWorkflowInstance(null);
    setWorkflowHomeKey((k) => k + 1);
    setActivePage('workflow');
  };

  const openSettingsTab = (tab: string) => {
    setSettingsTabRequest(tab);
    setActivePage('settings');
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-background">
        <div className="text-center">
          <div className="flex justify-center mb-4 animate-pulse text-foreground">
            <AppLogo logoUrl={logoUrl} size={40} iconClassName="text-foreground" />
          </div>
          <div className="text-muted-foreground text-sm">Initializing authentication…</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-background">
        <div className="bg-card border border-red-200 rounded-lg shadow p-8 max-w-md w-full mx-4">
          <div className="text-red-600 text-xl mb-3">Authentication Error</div>
          <div className="text-foreground text-sm mb-5">{error}</div>
          <button
            onClick={loginWithKeycloak}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white py-2 rounded transition-colors"
          >
            Try Again
          </button>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <LoginScreen
        appName={appName}
        logoUrl={logoUrl}
        onSelect={loginWithOption}
      />
    );
  }

  const renderPage = () => {
    switch (activePage) {
      case 'explain':    return <ExplainPage />;
      case 'dash':       return <DashPage />;
      case 'dispatcher': return <DispatcherPage />;
      case 'workflow':
        return (
          <WorkflowPage
            editTarget={workflowEditTarget}
            homeKey={workflowHomeKey}
            onEditTargetApplied={() => setWorkflowEditTarget(null)}
            onInstancesChanged={() => setWorkflowRefreshKey((k) => k + 1)}
            onActiveInstanceChange={setSelectedWorkflowInstance}
          />
        );
      case 'settings':
        return (
          <SettingsPage
            requestedTab={settingsTabRequest}
            onRequestedTabApplied={() => setSettingsTabRequest(null)}
          />
        );
      case 'help':       return <HelpPage />;
      default:           return <ExplainPage />;
    }
  };

  return (
    <div className="h-screen overflow-hidden bg-background">
      <TopBar onOpenSettingsTab={openSettingsTab} />
      <SideNav
        activePage={activePage}
        onNavigate={setActivePage}
        onOpenWorkflowHome={openWorkflowHome}
        onOpenWorkflowInstance={openWorkflowInstance}
        selectedWorkflowInstance={selectedWorkflowInstance}
        workflowRefreshKey={workflowRefreshKey}
      />
      {/* definite height + auto overflow: modules scroll internally, the page only scrolls
          when a view genuinely exceeds the viewport (no default page scrollbar). */}
      <main className="ml-44 mt-12 h-[calc(100vh-3rem)] flex flex-col overflow-auto">
        {renderPage()}
      </main>
    </div>
  );
}

export default function App() {
  return <AppContent />;
}
