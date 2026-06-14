import React from 'react';

/** A "label : control" group used across module filter bars. */
export function FilterField({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-1">
      <label className="filter-label">{label}:</label>
      {children}
    </div>
  );
}

/** FilterField wrapping the standard text filter input. */
export function FilterText({
  label,
  value,
  onChange,
  placeholder,
  width = 'w-36',
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  width?: string;
}) {
  return (
    <FilterField label={label}>
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={`text-sm field px-2 py-1 ${width} bg-card`}
      />
    </FilterField>
  );
}
