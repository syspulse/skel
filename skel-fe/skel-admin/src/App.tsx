import React, { useState } from 'react';
import { useAuth } from './auth/useAuth';
import { SideNav, NavPage } from './components/SideNav';
import { TopBar } from './components/TopBar';
import { ExplainPage } from './explain/ExplainPage';
import { DashPage } from './dash/DashPage';
import { HelpPage } from './pages/HelpPage';
import { SettingsPage } from './pages/SettingsPage';

function AppContent() {
  const { isLoading, error, isAuthenticated, login } = useAuth();
  const [activePage, setActivePage] = useState<NavPage>('explain');

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-background">
        <div className="text-center">
          <div className="text-4xl mb-4 animate-pulse">🔍</div>
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
          <button onClick={login} className="w-full bg-blue-600 hover:bg-blue-700 text-white py-2 rounded transition-colors">
            Try Again
          </button>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-background">
        <div className="bg-card border border-border rounded-lg shadow p-8 max-w-sm w-full mx-4 text-center">
          <div className="text-4xl mb-4">🔍</div>
          <h1 className="text-xl text-foreground mb-2">admin</h1>
          <p className="text-muted-foreground text-sm mb-6">Please sign in to continue.</p>
          <button onClick={login} className="w-full bg-blue-600 hover:bg-blue-700 text-white py-2 rounded transition-colors">
            Sign In
          </button>
        </div>
      </div>
    );
  }

  const renderPage = () => {
    switch (activePage) {
      case 'explain':  return <ExplainPage />;
      case 'dash':     return <DashPage />;
      case 'settings': return <SettingsPage />;
      case 'help':     return <HelpPage />;
      default:         return <ExplainPage />;
    }
  };

  return (
    <div className="min-h-screen bg-background">
      <TopBar />
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
