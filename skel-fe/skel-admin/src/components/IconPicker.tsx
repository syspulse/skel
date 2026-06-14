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
  svg('<circle cx="12" cy="8" r="3.5"/><path d="M5 20a7 7 0 0 1 14 0"/>'),
  svg('<path d="M12 21C5 16 3 12 3 8.5A4.5 4.5 0 0 1 12 6a4.5 4.5 0 0 1 9 2.5C21 12 19 16 12 21z"/>'),
  svg('<path d="M3 11l9-7 9 7"/><path d="M5 10v10h14V10"/>'),
  svg('<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 7l9 6 9-6"/>'),
  svg('<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 9h18M8 3v4M16 3v4"/>'),
  svg('<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v4h4"/>'),
  svg('<path d="M3 6h6l2 2h10v11H3z"/>'),
  svg('<path d="M4 5h16v11H9l-5 4z"/>'),
  svg('<path d="M3 11V3h8l10 10-8 8z"/><circle cx="7.5" cy="7.5" r="1.5"/>'),
  svg('<path d="M2 8.5a15 15 0 0 1 20 0M5 12a10 10 0 0 1 14 0M8.5 15.5a5 5 0 0 1 7 0"/><circle cx="12" cy="19" r="1"/>'),
  svg('<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9l3 3-3 3M13 15h4"/>'),
  svg('<path d="M12 21s7-6 7-11a7 7 0 0 0-14 0c0 5 7 11 7 11z"/><circle cx="12" cy="10" r="2.5"/>'),
];

// Default node icons (flat theme) when an entity has no icon of its own.
export const DEFAULT_SCHEMA_ICON = svg('<path d="M12 3 3 7l9 4 9-4z"/><path d="M3 12l9 4 9-4M3 17l9 4 9-4"/>'); // layers (detector schema / template)
export const DEFAULT_CONFIG_ICON = svg('<circle cx="12" cy="12" r="3"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M19 5l-2 2M7 17l-2 2"/>'); // gear (detector config)
// Workflow-level defaults: schema = flow/diagram template, config = running instance (play).
export const DEFAULT_WF_SCHEMA_ICON = svg('<rect x="3" y="4" width="6" height="5" rx="1"/><rect x="15" y="4" width="6" height="5" rx="1"/><rect x="9" y="15" width="6" height="5" rx="1"/><path d="M6 9v1.5a2 2 0 0 0 2 2h2.5M18 9v1.5a2 2 0 0 1-2 2H13.5M12 12.5V15"/>');
export const DEFAULT_WF_CONFIG_ICON = svg('<circle cx="12" cy="12" r="9"/><path d="M10 8.5l5.5 3.5-5.5 3.5z"/>');

interface IconPickerProps {
  value?: string;
  onChange: (icon: string | undefined) => void;
  /** how many icons to offer (default: all) */
  num?: number;
  /** grid width, in number of icons per row (default: 8) */
  w?: number;
  /** grid height, in number of icon rows shown before scrolling (default: all rows) */
  h?: number;
  /** placeholder for the custom url/svg input */
  placeholder?: string;
}

// icon cell box height (h-7 = 1.75rem) and grid gap (gap-1 = 0.25rem), used to size `h` rows.
const CELL_REM = 1.75;
const GAP_REM = 0.25;

/** Render an icon string as image (url/data/svg) or as text/emoji. */
export function renderIcon(icon: string | undefined, size = 18): React.ReactNode {
  if (!icon || !icon.trim()) return null;
  const s = icon.trim();
  if (s.toLowerCase().startsWith('<svg')) {
    return (
      <span className="icon-svg" style={{ width: size, height: size }} dangerouslySetInnerHTML={{ __html: s }} />
    );
  }
  if (s.startsWith('http') || s.startsWith('/') || s.startsWith('data:')) {
    return <img src={s} alt="icon" width={size} height={size} className="object-contain" />;
  }
  return <span style={{ fontSize: size }} className="leading-none">{s}</span>;
}

export function IconPicker({ value, onChange, num, w = 8, h, placeholder }: IconPickerProps) {
  const { t } = useTranslation();
  const icons = num != null ? QUICK_ICONS.slice(0, num) : QUICK_ICONS;
  const gridStyle: React.CSSProperties = { gridTemplateColumns: `repeat(${w}, minmax(0, 1fr))` };
  if (h != null) {
    gridStyle.maxHeight = `calc(${h} * ${CELL_REM}rem + ${Math.max(h - 1, 0)} * ${GAP_REM}rem)`;
    gridStyle.overflowY = 'auto';
  }
  return (
    <div className="icon-picker">
      <div className="icon-grid" style={gridStyle}>
        {icons.map((ic, i) => (
          <button
            key={i}
            type="button"
            onClick={() => onChange(ic)}
            className={`icon-cell ${value === ic ? 'icon-cell-on' : 'icon-cell-off'}`}
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
          placeholder={placeholder ?? t('workflow.editor.iconUrl')}
          className="flex-1 field-compact"
        />
        {value && <span className="icon-preview">{renderIcon(value, 18)}</span>}
      </div>
    </div>
  );
}
