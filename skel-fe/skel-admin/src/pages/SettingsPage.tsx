import React, { useEffect, useState } from 'react';
import { useAuth } from '../auth/useAuth';
import { useTheme, Theme } from '../theme/ThemeContext';
import { useApp, DEFAULT_APP_NAME } from '../theme/AppContext';
import { IconReset, IconLamp } from '../components/Icons';

const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED !== 'false';

function getStoredOrEnv(key: string, envVal: string): string {
  return localStorage.getItem(key) || envVal || '';
}

type Tab = 'profile' | 'api';

// ── Themes ────────────────────────────────────────────────────────────────────

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
    <div>
      <div className="text-xs text-muted-foreground mb-3">appearance</div>
      <div className="flex gap-3">
        {THEMES.map(({ id, label, desc, preview }) => (
          <button
            key={id}
            onClick={() => setTheme(id)}
            className={`flex-1 border-2 rounded-lg p-3 text-left transition-all
              ${theme === id ? 'border-blue-500' : 'border-border hover:border-muted-foreground'}`}
          >
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

// ── Logo preview (mirrors TopBar rendering) ───────────────────────────────────

function LogoPreview({ logoUrl }: { logoUrl: string }) {
  if (!logoUrl.trim()) {
    return (
      <span className="inline-flex items-center justify-center text-muted-foreground">
        <IconLamp size={32} />
      </span>
    );
  }
  const s = logoUrl.trim();
  if (s.toLowerCase().startsWith('<svg')) {
    return (
      <span
        className="inline-flex items-center justify-center w-8 h-8 [&>svg]:w-full [&>svg]:h-full"
        dangerouslySetInnerHTML={{ __html: s }}
      />
    );
  }
  return (
    <img
      src={s}
      alt="logo preview"
      width={32}
      height={32}
      className="object-contain"
      onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
    />
  );
}

// ── Profile tab ───────────────────────────────────────────────────────────────

function ProfileTab() {
  const { user, isAuthenticated } = useAuth();
  const { appName, logoUrl, setAppName, setLogoUrl, resetBranding } = useApp();

  return (
    <div className="space-y-6">
      {/* Appearance */}
      <div className="bg-card border border-border rounded shadow-sm p-6">
        <ThemeSection />
      </div>

      {/* Branding */}
      <div className="bg-card border border-border rounded shadow-sm p-6 space-y-4">
        <div className="text-xs text-muted-foreground mb-1">branding</div>

        <div>
          <label className="block text-sm text-foreground mb-1">app name</label>
          <input
            type="text"
            value={appName}
            onChange={(e) => setAppName(e.target.value)}
            placeholder={DEFAULT_APP_NAME}
            className="w-full text-sm border border-input rounded px-3 py-2 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
          />
        </div>

        <div>
          <label className="block text-sm text-foreground mb-1">logo</label>
          <div className="flex items-center gap-3">
            <input
              type="text"
              value={logoUrl}
              onChange={(e) => setLogoUrl(e.target.value)}
              placeholder="URL to SVG/PNG, inline <svg ...>, or data: URI"
              className="flex-1 text-sm border border-input rounded px-3 py-2 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
            />
            <div className="w-10 h-10 flex items-center justify-center border border-border rounded bg-muted shrink-0">
              <LogoPreview logoUrl={logoUrl} />
            </div>
          </div>
          <p className="text-xs text-muted-foreground mt-1">
            Leave empty to use the default icon.
          </p>
        </div>

        <div>
          <button
            onClick={resetBranding}
            className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-muted transition-colors"
          >
            <IconReset size={13} /> Reset branding
          </button>
        </div>
      </div>

      {/* Current Session */}
      <div className="bg-blue-50 border border-blue-200 rounded p-4">
        <div className="text-sm text-blue-800 mb-1">current session</div>
        <div className="text-sm text-blue-700 space-y-0.5">
          <div>auth mode: {AUTH_ENABLED ? 'Keycloak' : 'No-auth (Guest)'}</div>
          {isAuthenticated && user && (
            <>
              <div>user: {user.name}</div>
              {user.email && <div>email: {user.email}</div>}
              {user.roles && user.roles.length > 0 && (
                <div>roles: {user.roles.join(', ')}</div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

// ── API tab ───────────────────────────────────────────────────────────────────

interface ApiForm {
  apiUrl: string;
  dashApiUrl: string;
  keycloakUrl: string;
  keycloakRealm: string;
  keycloakClientId: string;
}

const API_DEFAULTS = {
  apiUrl:         import.meta.env.VITE_API_URL          || 'http://localhost:8080/api/v1/explain',
  dashApiUrl:     import.meta.env.VITE_DASH_API_URL     || 'http://localhost:8080/api/v1/dash',
  keycloakUrl:    import.meta.env.VITE_KEYCLOAK_URL     || 'http://localhost:8180',
  keycloakRealm:  import.meta.env.VITE_KEYCLOAK_REALM   || 'master',
  keycloakClientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'skel-admin',
};

function ApiTab() {
  const [form, setForm] = useState<ApiForm>(() => ({
    apiUrl:           getStoredOrEnv('VITE_API_URL',          API_DEFAULTS.apiUrl),
    dashApiUrl:       getStoredOrEnv('VITE_DASH_API_URL',     API_DEFAULTS.dashApiUrl),
    keycloakUrl:      getStoredOrEnv('VITE_KEYCLOAK_URL',     API_DEFAULTS.keycloakUrl),
    keycloakRealm:    getStoredOrEnv('VITE_KEYCLOAK_REALM',   API_DEFAULTS.keycloakRealm),
    keycloakClientId: getStoredOrEnv('VITE_KEYCLOAK_CLIENT_ID', API_DEFAULTS.keycloakClientId),
  }));

  const set = <K extends keyof ApiForm>(key: K, lsKey: string, value: string) => {
    setForm((f) => ({ ...f, [key]: value }));
    localStorage.setItem(lsKey, value);
  };

  const handleReset = () => {
    localStorage.removeItem('VITE_API_URL');
    localStorage.removeItem('VITE_DASH_API_URL');
    localStorage.removeItem('VITE_KEYCLOAK_URL');
    localStorage.removeItem('VITE_KEYCLOAK_REALM');
    localStorage.removeItem('VITE_KEYCLOAK_CLIENT_ID');
    setForm({ ...API_DEFAULTS });
  };

  return (
    <div className="space-y-6">
      <div className="bg-card border border-border rounded shadow-sm p-6 space-y-5">
        <div>
          <label className="block text-sm text-foreground mb-1">Explain API URL</label>
          <input
            type="text"
            value={form.apiUrl}
            onChange={(e) => set('apiUrl', 'VITE_API_URL', e.target.value)}
            className="w-full text-sm border border-input rounded px-3 py-2 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
          />
          <p className="text-xs text-muted-foreground mt-1">
            default: <code>{API_DEFAULTS.apiUrl}</code>
          </p>
        </div>

        <div>
          <label className="block text-sm text-foreground mb-1">Dash API URL</label>
          <input
            type="text"
            value={form.dashApiUrl}
            onChange={(e) => set('dashApiUrl', 'VITE_DASH_API_URL', e.target.value)}
            className="w-full text-sm border border-input rounded px-3 py-2 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
          />
          <p className="text-xs text-muted-foreground mt-1">
            default: <code>{API_DEFAULTS.dashApiUrl}</code>
          </p>
        </div>

        {AUTH_ENABLED && (
          <>
            <hr className="border-border" />
            <div className="text-xs text-muted-foreground">keycloak</div>

            <div>
              <label className="block text-sm text-foreground mb-1">URL</label>
              <input
                type="text"
                value={form.keycloakUrl}
                onChange={(e) => set('keycloakUrl', 'VITE_KEYCLOAK_URL', e.target.value)}
                className="w-full text-sm border border-input rounded px-3 py-2 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
              />
            </div>

            <div>
              <label className="block text-sm text-foreground mb-1">realm</label>
              <input
                type="text"
                value={form.keycloakRealm}
                onChange={(e) => set('keycloakRealm', 'VITE_KEYCLOAK_REALM', e.target.value)}
                className="w-full text-sm border border-input rounded px-3 py-2 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            </div>

            <div>
              <label className="block text-sm text-foreground mb-1">client id</label>
              <input
                type="text"
                value={form.keycloakClientId}
                onChange={(e) => set('keycloakClientId', 'VITE_KEYCLOAK_CLIENT_ID', e.target.value)}
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

      <div className="flex items-center gap-3">
        <button
          onClick={handleReset}
          className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-muted transition-colors"
        >
          <IconReset size={13} /> Reset to defaults
        </button>
      </div>

      <p className="text-xs text-muted-foreground">
        Changes are saved automatically. Keycloak config changes require a page reload.
      </p>
    </div>
  );
}

// ── SettingsPage ──────────────────────────────────────────────────────────────

export function SettingsPage() {
  const [tab, setTab] = useState<Tab>('profile');

  const tabs: { id: Tab; label: string }[] = [
    { id: 'profile', label: 'profile' },
    { id: 'api',     label: 'api' },
  ];

  return (
    <div className="w-full px-6 py-8 space-y-6">
      <h1 className="text-xl text-foreground">Settings</h1>

      {/* Tab bar */}
      <div className="flex border-b border-border">
        {tabs.map(({ id, label }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            className={`px-4 py-2 text-sm transition-colors border-b-2 -mb-px
              ${tab === id
                ? 'border-blue-500 text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="max-w-2xl">
        {tab === 'profile' && <ProfileTab />}
        {tab === 'api'     && <ApiTab />}
      </div>
    </div>
  );
}
