import React, { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { UserProfileTab } from '../auth/UserProfileTab';
import { useTheme } from '../theme/ThemeContext';
import { THEME_NAMES, PALETTE_SWATCHES, type Theme } from '../theme/palettes';
import { useApp, DEFAULT_APP_NAME } from '../theme/AppContext';
import { AppLogo } from '../components/AppBrand';
import { IconReset } from '../components/Icons';
import { ModulePage } from '../components/ModulePage';
import i18n from '../i18n';
import { usePageSize, PAGE_SIZE_OPTIONS } from './PageSizeContext';
import { useTimestampFormat } from './TimestampFormatContext';
import { useWorkflowGrid, DEFAULT_GRID_SIZE } from './WorkflowGridContext';
import { TimestampFormatSelect } from '../components/TimestampFormatSelect';

const AUTH_ENABLED = import.meta.env.VITE_AUTH_ENABLED !== 'false';

function getStoredOrEnv(key: string, envVal: string): string {
  return localStorage.getItem(key) || envVal || '';
}

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
          <div className="absolute z-50 left-0 top-full mt-1 w-full popover overflow-y-auto max-h-64">
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
        className="text-xs field px-2.5 py-1.5 w-52 bg-card cursor-pointer"
      >
        {PAGE_SIZE_OPTIONS.map((s) => (
          <option key={s} value={s}>{s} {t('pagination.perPage')}</option>
        ))}
      </select>
    </div>
  );
}

// ── Timestamp format selector ─────────────────────────────────────────────────

function TimestampFormatSection() {
  const { t } = useTranslation();
  const { formatPattern, setFormatPattern } = useTimestampFormat();

  return (
    <div>
      <div className="text-xs text-muted-foreground mb-1.5">{t('settings.timestampFormat.label')}</div>
      <TimestampFormatSelect value={formatPattern} onChange={setFormatPattern} />
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
        className="text-xs field px-2.5 py-1.5 w-52 bg-card cursor-pointer"
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
  const { appName, logoUrl, setAppName, setLogoUrl, resetBranding } = useApp();

  return (
    <div className="space-y-3">
      <div className="surface p-3 space-y-3">
        <ThemeSection />
        <LanguageSection />
        <PageSizeSection />
        <TimestampFormatSection />
      </div>

      <div className="surface p-3 space-y-2">
        <div className="text-xs text-muted-foreground">{t('settings.branding')}</div>

        <div>
          <label className="block text-xs text-foreground mb-0.5">{t('settings.appName')}</label>
          <input
            type="text"
            value={appName}
            onChange={(e) => setAppName(e.target.value)}
            placeholder={DEFAULT_APP_NAME}
            className="w-full text-sm field px-2 py-1 bg-card"
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
              className="flex-1 text-sm field px-2 py-1 bg-card font-mono"
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

    </div>
  );
}

// ── Workflow tab ──────────────────────────────────────────────────────────────

function WorkflowTab() {
  const { t } = useTranslation();
  const { gridSize, setGridSize, snapToGrid, setSnapToGrid } = useWorkflowGrid();

  return (
    <div className="space-y-3">
      <div className="surface p-3 space-y-3">
        <div>
          <div className="text-xs text-muted-foreground mb-1.5">{t('settings.workflow.gridSize')}</div>
          <input
            type="number"
            min={2}
            max={200}
            value={gridSize}
            onChange={(e) => setGridSize(Math.max(2, Number(e.target.value) || DEFAULT_GRID_SIZE))}
            className="text-xs field px-2.5 py-1.5 w-52 bg-card"
          />
        </div>

        <label className="flex items-center gap-2 text-xs text-foreground cursor-pointer">
          <input
            type="checkbox"
            checked={snapToGrid}
            onChange={(e) => setSnapToGrid(e.target.checked)}
            className="cursor-pointer"
          />
          {t('settings.workflow.snapToGrid')}
        </label>
        
      </div>
    </div>
  );
}

// ── API tab ───────────────────────────────────────────────────────────────────

interface ApiForm {
  apiUrl: string;
  dashApiUrl: string;
  workflowApiUrl: string;
  dispatcherWsUrl: string;
  keycloakUrl: string;
  keycloakRealm: string;
  keycloakClientId: string;
}

const API_DEFAULTS = {
  explainApiUrl:   import.meta.env.VITE_EXPLAIN_API_URL   || 'http://localhost:8080/api/v1/explain',
  dashApiUrl:      import.meta.env.VITE_DASH_API_URL      || 'http://localhost:8080/api/v1/dash',
  workflowApiUrl:  import.meta.env.VITE_WORKFLOW_API_URL  || 'http://localhost:8080/api/v1/wf/ext',
  dispatcherWsUrl: import.meta.env.VITE_DISPATCHER_WS_URL || '',
  keycloakUrl:     import.meta.env.VITE_KEYCLOAK_URL      || 'http://localhost:8180',
  keycloakRealm:   import.meta.env.VITE_KEYCLOAK_REALM    || 'master',
  keycloakClientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID || 'skel-admin',
};

// One config row per editable endpoint. Drives form init, render and reset (no per-field duplication).
interface ApiFieldDef {
  key: keyof ApiForm;
  lsKey: string;      // localStorage / env key it persists to
  labelKey: string;   // i18n label
  def: string;        // default value (used for init + reset)
  showDefault?: boolean; // show the "default: …" hint under the input
  placeholder?: string;
}

const ENDPOINT_FIELDS: ApiFieldDef[] = [
  { key: 'apiUrl',          lsKey: 'VITE_EXPLAIN_API_URL',   labelKey: 'settings.explainApiUrl',   def: API_DEFAULTS.explainApiUrl,   showDefault: true },
  { key: 'dashApiUrl',      lsKey: 'VITE_DASH_API_URL',      labelKey: 'settings.dashApiUrl',      def: API_DEFAULTS.dashApiUrl,      showDefault: true },
  { key: 'workflowApiUrl',  lsKey: 'VITE_WORKFLOW_API_URL',  labelKey: 'settings.workflowApiUrl',  def: API_DEFAULTS.workflowApiUrl,  showDefault: true },
  { key: 'dispatcherWsUrl', lsKey: 'VITE_DISPATCHER_WS_URL', labelKey: 'settings.dispatcherWsUrl', def: API_DEFAULTS.dispatcherWsUrl, placeholder: 'ws://host:port/…' },
];

const KEYCLOAK_FIELDS: ApiFieldDef[] = [
  { key: 'keycloakUrl',      lsKey: 'VITE_KEYCLOAK_URL',       labelKey: 'settings.keycloakUrl',      def: API_DEFAULTS.keycloakUrl },
  { key: 'keycloakRealm',    lsKey: 'VITE_KEYCLOAK_REALM',     labelKey: 'settings.keycloakRealm',    def: API_DEFAULTS.keycloakRealm },
  { key: 'keycloakClientId', lsKey: 'VITE_KEYCLOAK_CLIENT_ID', labelKey: 'settings.keycloakClientId', def: API_DEFAULTS.keycloakClientId },
];

const ALL_FIELDS = [...ENDPOINT_FIELDS, ...KEYCLOAK_FIELDS];

const buildApiForm = (read: (f: ApiFieldDef) => string): ApiForm => {
  const o = {} as ApiForm;
  ALL_FIELDS.forEach((f) => { o[f.key] = read(f); });
  return o;
};

function ApiField({ field, value, onChange }: { field: ApiFieldDef; value: string; onChange: (v: string) => void }) {
  const { t } = useTranslation();
  return (
    <div>
      <label className="block text-xs text-foreground mb-0.5">{t(field.labelKey)}</label>
      <input
        type="text"
        value={value}
        placeholder={field.placeholder}
        onChange={(e) => onChange(e.target.value)}
        className="w-full text-sm field px-2 py-1 bg-card font-mono"
      />
      {field.showDefault && field.def && (
        <p className="text-xs text-muted-foreground mt-0.5">default: <code>{field.def}</code></p>
      )}
    </div>
  );
}

function ApiTab() {
  const { t } = useTranslation();
  const [form, setForm] = useState<ApiForm>(() => buildApiForm((f) => getStoredOrEnv(f.lsKey, f.def)));

  const set = (field: ApiFieldDef, value: string) => {
    setForm((cur) => ({ ...cur, [field.key]: value }));
    localStorage.setItem(field.lsKey, value);
  };

  const handleReset = () => {
    ALL_FIELDS.forEach((f) => localStorage.removeItem(f.lsKey));
    setForm(buildApiForm((f) => f.def));
  };

  return (
    <div className="space-y-2">
      <div className="surface p-3 space-y-2">
        {ENDPOINT_FIELDS.map((f) => (
          <ApiField key={f.key} field={f} value={form[f.key]} onChange={(v) => set(f, v)} />
        ))}

        {AUTH_ENABLED && (
          <>
            <hr className="border-border my-1" />
            <div className="text-xs text-muted-foreground">{t('settings.keycloak')}</div>
            {KEYCLOAK_FIELDS.map((f) => (
              <ApiField key={f.key} field={f} value={form[f.key]} onChange={(v) => set(f, v)} />
            ))}
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

interface SettingsPageProps {
  requestedTab?: string | null;
  onRequestedTabApplied?: () => void;
}

export function SettingsPage({ requestedTab, onRequestedTabApplied }: SettingsPageProps) {
  const { t } = useTranslation();

  const tabs = [
    { id: 'profile',     label: t('settings.profile') },
    { id: 'userProfile', label: t('settings.user') },
    { id: 'workflow',    label: t('settings.workflow.label') },
    { id: 'api',         label: t('settings.api') },
  ];

  return (
    <ModulePage
      title={t('settings.title')}
      tabs={tabs}
      defaultTab="profile"
      contentClassName="max-w-2xl"
      requestedTab={requestedTab}
      onRequestedTabApplied={onRequestedTabApplied}
    >
      {(tab) => (
        <>
          {tab === 'profile'     && <ProfileTab />}
          {tab === 'userProfile' && <UserProfileTab />}
          {tab === 'workflow'    && <WorkflowTab />}
          {tab === 'api'         && <ApiTab />}
        </>
      )}
    </ModulePage>
  );
}
