import React, { useState } from 'react';
import { useAuth } from './auth/useAuth';
import { LoginScreen } from './auth/LoginScreen';
import { useApp } from './theme/AppContext';
import { AppLogo } from './components/AppBrand';
import { SideNav, NavPage } from './components/SideNav';
import { TopBar } from './components/TopBar';
import { ExplainPage } from './explain/ExplainPage';
import { DashPage } from './dash/DashPage';
import { DispatcherPage } from './dispatcher/DispatcherPage';
import { HelpPage } from './pages/HelpPage';
import { SettingsPage } from './pages/SettingsPage';

function AppContent() {
  const { isLoading, error, isAuthenticated, loginWithOption, loginWithKeycloak } = useAuth();
  const { appName, logoUrl } = useApp();
  const [activePage, setActivePage] = useState<NavPage>('explain');
  const [settingsTabRequest, setSettingsTabRequest] = useState<string | null>(null);

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
    <div className="min-h-screen bg-background">
      <TopBar onOpenSettingsTab={openSettingsTab} />
      <SideNav activePage={activePage} onNavigate={setActivePage} />
      <main className="ml-44 mt-12 min-h-[calc(100vh-3rem)] flex flex-col">
        {renderPage()}
      </main>
    </div>
  );
}

export default function App() {
  return <AppContent />;
}
