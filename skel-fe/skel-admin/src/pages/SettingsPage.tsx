import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/useAuth';
import { useTheme } from '../theme/ThemeContext';
import { THEME_NAMES, PALETTE_SWATCHES, type Theme } from '../theme/palettes';
import { useApp, DEFAULT_APP_NAME } from '../theme/AppContext';
import { AppLogo } from '../components/AppBrand';
import { IconReset } from '../components/Icons';
import i18n from '../i18n';
import { usePageSize, PAGE_SIZE_OPTIONS } from '../settings/PageSizeContext';

const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED !== 'false';

function getStoredOrEnv(key: string, envVal: string): string {
  return localStorage.getItem(key) || envVal || '';
}

type Tab = 'profile' | 'api';

const LANGUAGES = [
  { value: 'en', label: 'English' },
  { value: 'de', label: 'Deutsch' },
  { value: 'ja', label: '日本語' },
];

// ── Theme dropdown ────────────────────────────────────────────────────────────

function Swatches({ theme }: { theme: Theme }) {
  const [c1, c2, c3] = PALETTE_SWATCHES[theme];
  return (
    <span className="flex gap-px shrink-0">
      <span className="w-3 h-3 rounded-sm border border-black/10" style={{ background: c1 }} />
      <span className="w-3 h-3 rounded-sm border border-black/10" style={{ background: c2 }} />
      <span className="w-3 h-3 rounded-sm border border-black/10" style={{ background: c3 }} />
    </span>
  );
}

function ThemeSection() {
  const { t } = useTranslation();
  const { theme, setTheme } = useTheme();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [open]);

  return (
    <div>
      <div className="text-xs text-muted-foreground mb-1.5">{t('settings.appearance')}</div>
      <div ref={ref} className="relative w-52">
        <button
          onClick={() => setOpen(o => !o)}
          className="w-full flex items-center gap-2 px-2.5 py-1.5 text-xs border border-input rounded bg-card text-foreground hover:bg-muted transition-colors"
        >
          <Swatches theme={theme} />
          <span className="flex-1 text-left capitalize">{theme}</span>
          <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.5" className="text-muted-foreground shrink-0">
            <path d="M2 4l4 4 4-4" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </button>

        {open && (
          <div className="absolute z-50 left-0 top-full mt-1 w-full bg-card border border-border rounded shadow-lg overflow-y-auto max-h-64">
            {THEME_NAMES.map(themeName => (
              <button
                key={themeName}
                onClick={() => { setTheme(themeName); setOpen(false); }}
                className={`w-full flex items-center gap-2 px-2.5 py-1.5 text-xs text-left hover:bg-muted transition-colors
                  ${themeName === theme ? 'bg-muted font-medium' : ''}`}
              >
                <Swatches theme={themeName} />
                <span className="capitalize">{themeName}</span>
                {themeName === theme && (
                  <svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" strokeWidth="2" className="ml-auto text-blue-500 shrink-0">
                    <path d="M1.5 5l2.5 2.5 5-5" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                )}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// ── Page size selector ────────────────────────────────────────────────────────

function PageSizeSection() {
  const { t } = useTranslation();
  const { pageSize, setPageSize } = usePageSize();

  return (
    <div>
      <div className="text-xs text-muted-foreground mb-1.5">{t('settings.defaultPageSize')}</div>
      <select
        value={pageSize}
        onChange={(e) => setPageSize(Number(e.target.value))}
        className="text-xs border border-input rounded px-2.5 py-1.5 w-52 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 cursor-pointer"
      >
        {PAGE_SIZE_OPTIONS.map((s) => (
          <option key={s} value={s}>{s} {t('pagination.perPage')}</option>
        ))}
      </select>
    </div>
  );
}

// ── Language selector ─────────────────────────────────────────────────────────

function LanguageSection() {
  const { t } = useTranslation();
  const currentLang = i18n.language?.split('-')[0] ?? 'en';

  return (
    <div>
      <div className="text-xs text-muted-foreground mb-1.5">{t('settings.language')}</div>
      <select
        value={LANGUAGES.some(l => l.value === currentLang) ? currentLang : 'en'}
        onChange={(e) => i18n.changeLanguage(e.target.value)}
        className="text-xs border border-input rounded px-2.5 py-1.5 w-52 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 cursor-pointer"
      >
        {LANGUAGES.map(({ value, label }) => (
          <option key={value} value={value}>{label}</option>
        ))}
      </select>
    </div>
  );
}

// ── Profile tab ───────────────────────────────────────────────────────────────

function ProfileTab() {
  const { t } = useTranslation();
  const { user, isAuthenticated } = useAuth();
  const { appName, logoUrl, setAppName, setLogoUrl, resetBranding } = useApp();

  return (
    <div className="space-y-3">
      <div className="bg-card border border-border rounded shadow-sm p-3 space-y-3">
        <ThemeSection />
        <LanguageSection />
        <PageSizeSection />
      </div>

      <div className="bg-card border border-border rounded shadow-sm p-3 space-y-2">
        <div className="text-xs text-muted-foreground">{t('settings.branding')}</div>

        <div>
          <label className="block text-xs text-foreground mb-0.5">{t('settings.appName')}</label>
          <input
            type="text"
            value={appName}
            onChange={(e) => setAppName(e.target.value)}
            placeholder={DEFAULT_APP_NAME}
            className="w-full text-sm border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
          />
        </div>

        <div>
          <label className="block text-xs text-foreground mb-0.5">{t('settings.logo')}</label>
          <div className="flex items-center gap-2">
            <input
              type="text"
              value={logoUrl}
              onChange={(e) => setLogoUrl(e.target.value)}
              placeholder={t('settings.logoPlaceholder')}
              className="flex-1 text-sm border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
            />
            <div className="w-8 h-8 flex items-center justify-center border border-border rounded bg-muted shrink-0 text-muted-foreground">
              <AppLogo logoUrl={logoUrl} size={28} />
            </div>
          </div>
          <p className="text-xs text-muted-foreground mt-0.5">{t('settings.logoHint')}</p>
        </div>

        <button
          onClick={resetBranding}
          className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded border border-border text-muted-foreground hover:bg-muted transition-colors"
        >
          <IconReset size={12} /> {t('settings.resetBranding')}
        </button>
      </div>

      <div className="bg-blue-50 border border-blue-200 rounded p-2.5">
        <div className="text-xs text-blue-800 mb-0.5">{t('settings.currentSession')}</div>
        <div className="text-xs text-blue-700 space-y-0">
          <div>{t('settings.authMode')}: {AUTH_ENABLED ? t('settings.keycloakAuth') : t('settings.noAuth')}</div>
          {isAuthenticated && user && (
            <>
              <div>{t('settings.user')}: {user.name}</div>
              {user.email && <div>{t('settings.email')}: {user.email}</div>}
              {user.roles && user.roles.length > 0 && (
                <div>{t('settings.roles')}: {user.roles.join(', ')}</div>
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
  explainApiUrl:  import.meta.env.VITE_EXPLAIN_API_URL  || 'http://localhost:8080/api/v1/explain',
  dashApiUrl:     import.meta.env.VITE_DASH_API_URL     || 'http://localhost:8080/api/v1/dash',
  keycloakUrl:    import.meta.env.VITE_KEYCLOAK_URL     || 'http://localhost:8180',
  keycloakRealm:  import.meta.env.VITE_KEYCLOAK_REALM   || 'master',
  keycloakClientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'skel-admin',
};

function ApiTab() {
  const { t } = useTranslation();
  const [form, setForm] = useState<ApiForm>(() => ({
    apiUrl:           getStoredOrEnv('VITE_EXPLAIN_API_URL',  API_DEFAULTS.explainApiUrl),
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
    localStorage.removeItem('VITE_EXPLAIN_API_URL');
    localStorage.removeItem('VITE_DASH_API_URL');
    localStorage.removeItem('VITE_KEYCLOAK_URL');
    localStorage.removeItem('VITE_KEYCLOAK_REALM');
    localStorage.removeItem('VITE_KEYCLOAK_CLIENT_ID');
    setForm({ ...API_DEFAULTS });
  };

  return (
    <div className="space-y-2">
      <div className="bg-card border border-border rounded shadow-sm p-3 space-y-2">
        <div>
          <label className="block text-xs text-foreground mb-0.5">{t('settings.explainApiUrl')}</label>
          <input
            type="text"
            value={form.apiUrl}
            onChange={(e) => set('apiUrl', 'VITE_EXPLAIN_API_URL', e.target.value)}
            className="w-full text-sm border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
          />
          <p className="text-xs text-muted-foreground mt-0.5">
            default: <code>{API_DEFAULTS.explainApiUrl}</code>
          </p>
        </div>

        <div>
          <label className="block text-xs text-foreground mb-0.5">{t('settings.dashApiUrl')}</label>
          <input
            type="text"
            value={form.dashApiUrl}
            onChange={(e) => set('dashApiUrl', 'VITE_DASH_API_URL', e.target.value)}
            className="w-full text-sm border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
          />
          <p className="text-xs text-muted-foreground mt-0.5">
            default: <code>{API_DEFAULTS.dashApiUrl}</code>
          </p>
        </div>

        {AUTH_ENABLED && (
          <>
            <hr className="border-border my-1" />
            <div className="text-xs text-muted-foreground">{t('settings.keycloak')}</div>

            <div>
              <label className="block text-xs text-foreground mb-0.5">{t('settings.keycloakUrl')}</label>
              <input
                type="text"
                value={form.keycloakUrl}
                onChange={(e) => set('keycloakUrl', 'VITE_KEYCLOAK_URL', e.target.value)}
                className="w-full text-sm border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
              />
            </div>

            <div>
              <label className="block text-xs text-foreground mb-0.5">{t('settings.keycloakRealm')}</label>
              <input
                type="text"
                value={form.keycloakRealm}
                onChange={(e) => set('keycloakRealm', 'VITE_KEYCLOAK_REALM', e.target.value)}
                className="w-full text-sm border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            </div>

            <div>
              <label className="block text-xs text-foreground mb-0.5">{t('settings.keycloakClientId')}</label>
              <input
                type="text"
                value={form.keycloakClientId}
                onChange={(e) => set('keycloakClientId', 'VITE_KEYCLOAK_CLIENT_ID', e.target.value)}
                className="w-full text-sm border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
              />
            </div>
          </>
        )}

        {!AUTH_ENABLED && (
          <div className="text-xs text-muted-foreground italic">
            {t('settings.noAuthNote')}
          </div>
        )}
      </div>

      <button
        onClick={handleReset}
        className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded border border-border text-muted-foreground hover:bg-muted transition-colors"
      >
        <IconReset size={12} /> {t('settings.resetToDefaults')}
      </button>

      <p className="text-xs text-muted-foreground">
        {t('settings.autoSaveNote')}
      </p>
    </div>
  );
}

// ── SettingsPage ──────────────────────────────────────────────────────────────

export function SettingsPage() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<Tab>('profile');

  const tabs: { id: Tab; labelKey: string }[] = [
    { id: 'profile', labelKey: 'settings.profile' },
    { id: 'api',     labelKey: 'settings.api' },
  ];

  return (
    <div className="w-full px-4 py-3 space-y-2">
      <h1 className="text-lg text-foreground">{t('settings.title')}</h1>

      <div className="flex border-b border-border gap-1">
        {tabs.map(({ id, labelKey }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            className={`px-3 py-1 text-xs transition-colors border-b-2 -mb-px
              ${tab === id
                ? 'border-blue-500 text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
          >
            {t(labelKey)}
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
