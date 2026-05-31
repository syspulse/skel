import React, { useState } from 'react';
import { useAuth } from './auth/useAuth';
import { SideNav, NavPage } from './components/SideNav';
import { TopBar } from './components/TopBar';
import { ExplainPage } from './pages/ExplainPage';
import { HelpPage } from './pages/HelpPage';
import { SettingsPage } from './pages/SettingsPage';

function AppContent() {
  const { isLoading, error, isAuthenticated, login } = useAuth();
  const [activePage, setActivePage] = useState<NavPage>('explain');

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-100">
        <div className="text-center">
          <div className="text-4xl mb-4 animate-pulse">🔍</div>
          <div className="text-gray-600 text-sm">Initializing authentication…</div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-100">
        <div className="bg-white border border-red-200 rounded-lg shadow p-8 max-w-md w-full mx-4">
          <div className="text-red-600 text-xl mb-3 font-semibold">
            Authentication Error
          </div>
          <div className="text-gray-700 text-sm mb-5">{error}</div>
          <button
            onClick={login}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 rounded transition-colors"
          >
            Try Again
          </button>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-gray-100">
        <div className="bg-white border border-gray-200 rounded-lg shadow p-8 max-w-sm w-full mx-4 text-center">
          <div className="text-4xl mb-4">🔍</div>
          <h1 className="text-xl font-semibold text-gray-800 mb-2">Explain Admin</h1>
          <p className="text-gray-500 text-sm mb-6">Please sign in to continue.</p>
          <button
            onClick={login}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 rounded transition-colors"
          >
            Sign In
          </button>
        </div>
      </div>
    );
  }

  const renderPage = () => {
    switch (activePage) {
      case 'explain':
        return <ExplainPage />;
      case 'settings':
        return <SettingsPage />;
      case 'help':
        return <HelpPage />;
      default:
        return <ExplainPage />;
    }
  };

  return (
    <div className="min-h-screen bg-gray-100">
      {/* Top bar */}
      <TopBar />

      {/* Side nav */}
      <SideNav activePage={activePage} onNavigate={setActivePage} />

      {/* Main content */}
      <main className="ml-52 mt-14 min-h-[calc(100vh-3.5rem)] flex flex-col">
        {renderPage()}
      </main>
    </div>
  );
}

export default function App() {
  return <AppContent />;
}
