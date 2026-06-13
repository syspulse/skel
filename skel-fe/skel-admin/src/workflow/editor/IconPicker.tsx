import React from 'react';
import { useTranslation } from 'react-i18next';

// Flat, monotone (single-color, currentColor) abstract line icons. Kept to 3 lines in the UI.
const svg = (paths: string): string =>
  `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`;

export const QUICK_ICONS: string[] = [
  svg('<circle cx="12" cy="12" r="8"/>'),
  svg('<rect x="5" y="5" width="14" height="14" rx="2"/>'),
  svg('<path d="M12 4 20 19 4 19Z"/>'),
  svg('<path d="M12 3l8 4.5v9L12 21l-8-4.5v-9z"/>'),
  svg('<path d="M12 3l2.4 6 6 .4-4.6 3.9 1.5 5.9L12 16l-5.3 3.1 1.5-5.9L3.6 9.4l6-.4z"/>'),
  svg('<path d="M13 3 4 14h7l-1 7 9-11h-7z"/>'),
  svg('<path d="M12 3l7 3v6c0 4-3 7-7 9-4-2-7-5-7-9V6z"/>'),
  svg('<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/>'),
  svg('<ellipse cx="12" cy="6" rx="7" ry="3"/><path d="M5 6v6c0 1.7 3.1 3 7 3s7-1.3 7-3V6M5 12v6c0 1.7 3.1 3 7 3s7-1.3 7-3v-6"/>'),
  svg('<path d="M6 16a4 4 0 1 1 1-7.9A5 5 0 0 1 17 8a3.5 3.5 0 0 1 1 6.9z"/>'),
  svg('<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>'),
  svg('<path d="M5 3v18l7-4 7 4V3z"/>'),
  svg('<path d="M12 3 3 7l9 4 9-4z"/><path d="M3 12l9 4 9-4M3 17l9 4 9-4"/>'),
  svg('<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>'),
  svg('<path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7L11 6"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7L13 18"/>'),
  svg('<path d="M3 4h18l-7 8v6l-4 2v-8z"/>'),
  svg('<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.7 21a2 2 0 0 1-3.4 0"/>'),
  svg('<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>'),
  svg('<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"/>'),
  svg('<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.6"/>'),
  svg('<path d="M3 12h4l3 8 4-16 3 8h4"/>'),
  svg('<path d="M4 7l8-4 8 4-8 4z"/><path d="M4 7v10l8 4 8-4V7"/>'),
  svg('<path d="M4 17l6-5-6-5"/><path d="M12 19h8"/>'),
  svg('<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>'),
];

// Default node icons (flat theme) when a detector has no icon of its own.
export const DEFAULT_SCHEMA_ICON = svg('<path d="M12 3 3 7l9 4 9-4z"/><path d="M3 12l9 4 9-4M3 17l9 4 9-4"/>'); // layers (template)
export const DEFAULT_CONFIG_ICON = svg('<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/>'); // gear (config)

interface IconPickerProps {
  value?: string;
  onChange: (icon: string | undefined) => void;
}

/** Render an icon string as image (url/data/svg) or as text/emoji. */
export function renderIcon(icon: string | undefined, size = 18): React.ReactNode {
  if (!icon || !icon.trim()) return null;
  const s = icon.trim();
  if (s.toLowerCase().startsWith('<svg')) {
    return (
      <span className="inline-flex items-center justify-center [&>svg]:w-full [&>svg]:h-full"
        style={{ width: size, height: size }} dangerouslySetInnerHTML={{ __html: s }} />
    );
  }
  if (s.startsWith('http') || s.startsWith('/') || s.startsWith('data:')) {
    return <img src={s} alt="icon" width={size} height={size} className="object-contain" />;
  }
  return <span style={{ fontSize: size }} className="leading-none">{s}</span>;
}

export function IconPicker({ value, onChange }: IconPickerProps) {
  const { t } = useTranslation();
  return (
    <div className="space-y-1.5">
      <div className="grid grid-cols-8 gap-1">
        {QUICK_ICONS.map((ic, i) => (
          <button
            key={i}
            type="button"
            onClick={() => onChange(ic)}
            className={`flex items-center justify-center h-7 rounded border transition-colors text-foreground
              ${value === ic ? 'border-blue-500 bg-blue-50 text-blue-600' : 'border-border hover:bg-muted'}`}
          >
            {renderIcon(ic, 16)}
          </button>
        ))}
      </div>
      <div className="flex items-center gap-2">
        <input
          type="text"
          value={value ?? ''}
          onChange={(e) => onChange(e.target.value || undefined)}
          placeholder={t('workflow.editor.iconUrl')}
          className="flex-1 text-xs border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
        />
        {value && (
          <span className="inline-flex items-center justify-center w-7 h-7 border border-border rounded bg-muted">
            {renderIcon(value, 18)}
          </span>
        )}
      </div>
    </div>
  );
}
