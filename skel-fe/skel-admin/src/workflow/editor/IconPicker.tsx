import React from 'react';
import { useTranslation } from 'react-i18next';

// Quick-selectable icons available in the Admin (kept to 3 lines max in the UI).
export const QUICK_ICONS: string[] = [
  '🔍', '🛡️', '⚙️', '📊', '🔔', '⚡', '🧠', '🔗',
  '📥', '📤', '✅', '⛔', '🚀', '🧩', '💾', '📡',
  '🔥', '🪙', '📈', '📉', '🔒', '🌐', '⏱️', '🎯',
];

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
        {QUICK_ICONS.map((ic) => (
          <button
            key={ic}
            type="button"
            onClick={() => onChange(ic)}
            className={`flex items-center justify-center h-7 rounded border text-base transition-colors
              ${value === ic ? 'border-blue-500 bg-blue-50' : 'border-border hover:bg-muted'}`}
            title={ic}
          >
            {ic}
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
