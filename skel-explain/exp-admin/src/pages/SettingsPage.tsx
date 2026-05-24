import React, { useEffect, useState } from 'react';
import { useAuth } from '../auth/useAuth';

const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED !== 'false';

function getStoredOrEnv(key: string, envVal: string): string {
  return localStorage.getItem(key) || envVal || '';
}

interface SettingsForm {
  apiUrl: string;
  keycloakUrl: string;
  keycloakRealm: string;
  keycloakClientId: string;
}

export function SettingsPage() {
  const { user, isAuthenticated } = useAuth();
  const [form, setForm] = useState<SettingsForm>({
    apiUrl: '',
    keycloakUrl: '',
    keycloakRealm: '',
    keycloakClientId: '',
  });
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    setForm({
      apiUrl: getStoredOrEnv(
        'VITE_API_URL',
        import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1/explain',
      ),
      keycloakUrl: getStoredOrEnv(
        'VITE_KEYCLOAK_URL',
        import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180',
      ),
      keycloakRealm: getStoredOrEnv(
        'VITE_KEYCLOAK_REALM',
        import.meta.env.VITE_KEYCLOAK_REALM || 'master',
      ),
      keycloakClientId: getStoredOrEnv(
        'VITE_KEYCLOAK_CLIENT_ID',
        import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'explain-admin',
      ),
    });
  }, []);

  const handleSave = () => {
    localStorage.setItem('VITE_API_URL', form.apiUrl);
    if (AUTH_ENABLED) {
      localStorage.setItem('VITE_KEYCLOAK_URL', form.keycloakUrl);
      localStorage.setItem('VITE_KEYCLOAK_REALM', form.keycloakRealm);
      localStorage.setItem('VITE_KEYCLOAK_CLIENT_ID', form.keycloakClientId);
    }
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  };

  const handleReset = () => {
    localStorage.removeItem('VITE_API_URL');
    localStorage.removeItem('VITE_KEYCLOAK_URL');
    localStorage.removeItem('VITE_KEYCLOAK_REALM');
    localStorage.removeItem('VITE_KEYCLOAK_CLIENT_ID');
    setForm({
      apiUrl: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1/explain',
      keycloakUrl: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8180',
      keycloakRealm: import.meta.env.VITE_KEYCLOAK_REALM || 'master',
      keycloakClientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'explain-admin',
    });
    setSaved(false);
  };

  return (
    <div className="max-w-2xl mx-auto px-6 py-8">
      <h1 className="text-xl font-semibold text-gray-800 mb-6">Settings</h1>

      {/* Current user info */}
      <div className="bg-blue-50 border border-blue-200 rounded p-4 mb-6">
        <div className="text-sm font-medium text-blue-800 mb-1">Current Session</div>
        <div className="text-sm text-blue-700 space-y-0.5">
          <div>
            Auth mode:{' '}
            <span className="font-medium">
              {AUTH_ENABLED ? 'Keycloak' : 'No-auth (Guest)'}
            </span>
          </div>
          {isAuthenticated && user && (
            <>
              <div>
                User: <span className="font-medium">{user.name}</span>
              </div>
              {user.email && (
                <div>
                  Email: <span className="font-medium">{user.email}</span>
                </div>
              )}
              {user.roles && user.roles.length > 0 && (
                <div>
                  Roles:{' '}
                  <span className="font-medium">{user.roles.join(', ')}</span>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      <div className="bg-white border border-gray-200 rounded shadow-sm p-6 space-y-5">
        {/* API URL */}
        <div>
          <label className="block text-sm font-semibold text-gray-700 mb-1">
            API Base URL
          </label>
          <input
            type="text"
            value={form.apiUrl}
            onChange={(e) => setForm((f) => ({ ...f, apiUrl: e.target.value }))}
            className="w-full text-sm border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
          />
          <p className="text-xs text-gray-500 mt-1">
            Default: <code>{import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1/explain'}</code>
          </p>
        </div>

        {/* Keycloak settings */}
        {AUTH_ENABLED && (
          <>
            <hr className="border-gray-100" />
            <div className="text-sm font-semibold text-gray-600 uppercase tracking-wide text-xs">
              Keycloak Configuration
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">
                Keycloak URL
              </label>
              <input
                type="text"
                value={form.keycloakUrl}
                onChange={(e) =>
                  setForm((f) => ({ ...f, keycloakUrl: e.target.value }))
                }
                className="w-full text-sm border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
              />
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">
                Realm
              </label>
              <input
                type="text"
                value={form.keycloakRealm}
                onChange={(e) =>
                  setForm((f) => ({ ...f, keycloakRealm: e.target.value }))
                }
                className="w-full text-sm border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1">
                Client ID
              </label>
              <input
                type="text"
                value={form.keycloakClientId}
                onChange={(e) =>
                  setForm((f) => ({ ...f, keycloakClientId: e.target.value }))
                }
                className="w-full text-sm border border-gray-300 rounded px-3 py-2 focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            </div>
          </>
        )}

        {!AUTH_ENABLED && (
          <div className="text-sm text-gray-500 italic">
            Running in no-auth mode. Keycloak settings are not used.
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="flex items-center gap-3 mt-5">
        <button
          onClick={handleSave}
          className="bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-5 py-2 rounded transition-colors"
        >
          Save to localStorage
        </button>
        <button
          onClick={handleReset}
          className="bg-gray-200 hover:bg-gray-300 text-gray-700 text-sm font-medium px-5 py-2 rounded transition-colors"
        >
          Reset to defaults
        </button>
        {saved && (
          <span className="text-green-600 text-sm font-medium">
            ✓ Saved! Reload the page for Keycloak changes to take effect.
          </span>
        )}
      </div>

      <p className="text-xs text-gray-400 mt-4">
        Note: API URL changes take effect immediately. Keycloak config changes require a page reload.
      </p>
    </div>
  );
}
