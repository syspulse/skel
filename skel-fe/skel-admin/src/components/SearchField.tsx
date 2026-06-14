import React, { useEffect, useRef, useState } from 'react';
import { IconSearch } from './Icons';

interface SearchFieldProps {
  value: string;
  onChange: (value: string) => void;
  onSearch: (query: string) => void;
  presets?: string[];
  placeholder?: string;
  className?: string;
}

export function SearchField({
  value,
  onChange,
  onSearch,
  presets = [],
  placeholder = 'search...',
  className = '',
}: SearchFieldProps) {
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

  const handleSelect = (preset: string) => {
    onChange(preset);
    onSearch(preset);
    setOpen(false);
  };

  return (
    <div ref={ref} className={`relative ${className}`}>
      <div className="flex items-center border border-input rounded bg-card focus-within:ring-1 focus-within:ring-blue-400">
        <span className="pl-2 text-muted-foreground shrink-0 pointer-events-none">
          <IconSearch size={14} />
        </span>
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') { onSearch(value); setOpen(false); }
            if (e.key === 'Escape') setOpen(false);
          }}
          placeholder={placeholder}
          className="text-sm px-2 py-1 flex-1 min-w-0 bg-transparent text-foreground focus:outline-none"
        />
        {presets.length > 0 && (
          <button
            type="button"
            onClick={() => setOpen(o => !o)}
            className="px-1.5 text-muted-foreground hover:text-foreground transition-colors shrink-0"
            tabIndex={-1}
          >
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M2 4l4 4 4-4" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
        )}
      </div>

      {open && presets.length > 0 && (
        <div className="absolute z-50 left-0 top-full mt-1 w-full popover overflow-y-auto max-h-48">
          {presets.map((preset) => (
            <button
              key={preset}
              type="button"
              onClick={() => handleSelect(preset)}
              className={`w-full text-left px-3 py-1.5 text-xs text-foreground hover:bg-muted transition-colors${
                preset === value ? ' bg-muted font-medium' : ''
              }`}
            >
              {preset}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
