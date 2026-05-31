import React from 'react';
import { IconClose, IconPlus } from './Icons';

interface MetaEditorProps {
  value: Record<string, unknown>;
  onChange: (meta: Record<string, unknown>) => void;
}

interface MetaRow {
  key: string;
  rawValue: string;
}

function toRawValue(v: unknown): string {
  if (typeof v === 'string') return v;
  return JSON.stringify(v);
}

function fromRawValue(raw: string): unknown {
  const trimmed = raw.trim();
  try {
    const parsed = JSON.parse(trimmed);
    if (typeof parsed !== 'string') return parsed;
  } catch {
    // not valid JSON → treat as plain string
  }
  return raw;
}

function metaToRows(meta: Record<string, unknown>): MetaRow[] {
  return Object.entries(meta).map(([key, value]) => ({
    key,
    rawValue: toRawValue(value),
  }));
}

function rowsToMeta(rows: MetaRow[]): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const row of rows) {
    if (row.key.trim()) {
      result[row.key.trim()] = fromRawValue(row.rawValue);
    }
  }
  return result;
}

export function MetaEditor({ value, onChange }: MetaEditorProps) {
  // Initialized once from value prop. Parent must remount this component (via key prop)
  // to load a different rule — no useEffect sync to avoid resetting rows mid-edit.
  const [rows, setRows] = React.useState<MetaRow[]>(() => metaToRows(value));

  const commit = (newRows: MetaRow[]) => {
    setRows(newRows);
    onChange(rowsToMeta(newRows));
  };

  const handleKeyChange = (idx: number, key: string) => {
    const newRows = rows.map((r, i) => (i === idx ? { ...r, key } : r));
    commit(newRows);
  };

  const handleValueChange = (idx: number, rawValue: string) => {
    const newRows = rows.map((r, i) => (i === idx ? { ...r, rawValue } : r));
    commit(newRows);
  };

  const handleAdd = () => {
    commit([...rows, { key: '', rawValue: '' }]);
  };

  const handleRemove = (idx: number) => {
    commit(rows.filter((_, i) => i !== idx));
  };

  return (
    <div className="space-y-1">
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs text-muted-foreground">
          meta
        </span>
        <button
          type="button"
          onClick={handleAdd}
          className="inline-flex items-center gap-1 text-xs bg-card hover:bg-muted-hover border border-border text-foreground px-2 py-0.5 rounded transition-colors"
        >
          <IconPlus size={12} /> Add
        </button>
      </div>

      {rows.length === 0 && (
        <div className="text-xs text-muted-foreground italic py-1">No meta entries</div>
      )}

      {rows.map((row, idx) => (
        <div key={idx} className="flex items-center gap-1">
          <input
            type="text"
            value={row.key}
            onChange={(e) => handleKeyChange(idx, e.target.value)}
            placeholder="key"
            className="text-xs border border-input rounded px-2 py-1 w-28 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400 font-mono"
          />
          <span className="text-muted-foreground text-xs">:</span>
          <input
            type="text"
            value={row.rawValue}
            onChange={(e) => handleValueChange(idx, e.target.value)}
            placeholder="value"
            className="text-xs border border-input rounded px-2 py-1 flex-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400"
          />
          <button
            type="button"
            onClick={() => handleRemove(idx)}
            className="text-muted-foreground hover:text-red-500 p-0.5 rounded transition-colors"
            title="Remove"
          >
            <IconClose size={14} />
          </button>
        </div>
      ))}
    </div>
  );
}
