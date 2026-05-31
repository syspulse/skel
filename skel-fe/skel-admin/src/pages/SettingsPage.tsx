import React, { useEffect, useState } from 'react';
import { useAuth } from '../auth/useAuth';
import { useTheme, Theme } from '../theme/ThemeContext';
import { IconSave, IconReset } from '../components/Icons';

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

const THEMES: { id: Theme; label: string; desc: string; preview: { nav: string; bg: string; card: string } }[] = [
  {
    id: 'light',
    label: 'Light',
    desc: 'Clean white',
    preview: { nav: 'hsl(240 4.8% 95.9%)', bg: 'hsl(0 0% 100%)', card: 'hsl(240 4.8% 91%)' },
  },
  {
    id: 'dark',
    label: 'Dark',
    desc: 'Dark mode',
    preview: { nav: 'hsl(240 10% 8%)', bg: 'hsl(240 10% 3.9%)', card: 'hsl(240 10% 6.5%)' },
  },
  {
    id: 'stone',
    label: 'Stone',
    desc: 'Warm neutral',
    preview: { nav: 'hsl(20 14% 22%)', bg: 'hsl(60 9% 97.8%)', card: 'hsl(0 0% 100%)' },
  },
];

function ThemeSection() {
  const { theme, setTheme } = useTheme();

  return (
    <div className="bg-card border border-border rounded shadow-sm p-6">
      <div className="text-xs text-muted-foreground uppercase tracking-wide mb-4">
        Appearance
      </div>
      <div className="flex gap-3">
        {THEMES.map(({ id, label, desc, preview }) => (
          <button
            key={id}
            onClick={() => setTheme(id)}
            className={`flex-1 border-2 rounded-lg p-3 text-left transition-all
              ${theme === id
                ? 'border-blue-500'
                : 'border-border hover:border-muted-foreground'
              }`}
          >
            {/* Color preview */}
            <div
              className="flex gap-0 mb-2.5 rounded overflow-hidden h-9 border border-border"
              style={{ background: preview.bg }}
            >
              <div className="w-6 shrink-0" style={{ background: preview.nav }} />
              <div className="flex-1 p-1">
                <div className="w-full h-full rounded-sm" style={{ background: preview.card }} />
              </div>
            </div>
            <div className="flex items-center justify-between">
              <div>
                <div className="text-xs text-foreground">{label}</div>
                <div className="text-xs text-muted-foreground">{desc}</div>
              </div>
              {theme === id && (
                <div className="w-4 h-4 rounded-full bg-blue-500 flex items-center justify-center shrink-0">
                  <svg width="8" height="8" viewBox="0 0 8 8" fill="none">
                    <path d="M1 4l2 2 4-4" stroke="white" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                </div>
              )}
            </div>
          </button>
        ))}
      </div>
    </div>
  );
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
    <div className="max-w-2xl mx-auto px-6 py-8 space-y-6">
      <h1 className="text-xl text-foreground">Settings</h1>

      {/* Theme */}
      <ThemeSection />

      {/* Current session */}
      <div className="bg-blue-50 border border-blue-200 rounded p-4">
        <div className="text-sm text-blue-800 mb-1">Current Session</div>
        <div className="text-sm text-blue-700 space-y-0.5">
          <div>
            Auth mode:{' '}
            <span className="">
              {AUTH_ENABLED ? 'Keycloak' : 'No-auth (Guest)'}
            </span>
          </div>
          {isAuthenticated && user && (
            <>
              <div>
                User: <span className="">{user.name}</span>
              </div>
              {user.email && (
                <div>
                  Email: <span className="">{user.email}</span>
                </div>
              )}
              {user.roles && user.roles.length > 0 && (
                <div>
                  Roles:{' '}
                  <span className="">{user.roles.join(', ')}</span>
                </div>
              )}
            </>
          )}
        </div>
      </div>

      <div className="bg-card border border-border rounded shadow-sm p-6 space-y-5">
        {/* API URL */}
        <div>
          <label className="block text-sm text-foreground mb-1">
            API Base URL
          </label>
          <input
            type="text"
            value={form.apiUrl}
            onChange={(e) => setForm((f) => ({ ...f, apiUrl: e.target.value }))}
            className="w-full text-sm border border-input rounded px-3 py-2 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
          />
          <p className="text-xs text-muted-foreground mt-1">
            Default: <code>{import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1/explain'}</code>
          </p>
        </div>

        {/* Keycloak settings */}
        {AUTH_ENABLED && (
          <>
            <hr className="border-border" />
            <div className="text-xs text-muted-foreground uppercase tracking-wide">
              Keycloak Configuration
            </div>

            <div>
              <label className="block text-sm text-foreground mb-1">
                Keycloak URL
              </label>
              <input
                type="text"
                value={form.keycloakUrl}
                onChange={(e) =>
                  setForm((f) => ({ ...f, keycloakUrl: e.target.value }))
                }
                className="w-full text-sm border border-input rounded px-3 py-2 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
              />
            </div>

            <div>
              <label className="block text-sm text-foreground mb-1">
                Realm
              </label>
              <input
                type="text"
                value={form.keycloakRealm}
                onChange={(e) =>
                  setForm((f) => ({ ...f, keycloakRealm: e.target.value }))
                }
                className="w-full text-sm border border-input rounded px-3 py-2 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            </div>

            <div>
              <label className="block text-sm text-foreground mb-1">
                Client ID
              </label>
              <input
                type="text"
                value={form.keycloakClientId}
                onChange={(e) =>
                  setForm((f) => ({ ...f, keycloakClientId: e.target.value }))
                }
                className="w-full text-sm border border-input rounded px-3 py-2 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            </div>
          </>
        )}

        {!AUTH_ENABLED && (
          <div className="text-sm text-muted-foreground italic">
            Running in no-auth mode. Keycloak settings are not used.
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="flex items-center gap-3">
        <button
          onClick={handleSave}
          className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 transition-colors"
        >
          <IconSave size={13} /> Save to localStorage
        </button>
        <button
          onClick={handleReset}
          className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-muted transition-colors"
        >
          <IconReset size={13} /> Reset to defaults
        </button>
        {saved && (
          <span className="text-green-600 text-xs">
            ✓ Saved! Reload the page for Keycloak changes to take effect.
          </span>
        )}
      </div>

      <p className="text-xs text-muted-foreground">
        Note: API URL changes take effect immediately. Keycloak config changes require a page reload.
      </p>
    </div>
  );
}
