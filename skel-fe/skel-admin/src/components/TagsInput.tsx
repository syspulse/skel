import React, { useRef, useState } from 'react';
import { IconClose } from './Icons';

interface TagsInputProps {
  value: string[];
  onChange: (tags: string[]) => void;
  placeholder?: string;
  readOnly?: boolean;
  className?: string;
}

/**
 * AntDesign-style tag editor: renders each tag as a closable label chip. Type + Enter/comma to add;
 * click a chip to select it; press its [x] (or Backspace on empty input) to delete it.
 */
export function TagsInput({ value, onChange, placeholder = 'add tag…', readOnly = false, className = '' }: TagsInputProps) {
  const [input, setInput] = useState('');
  const [selected, setSelected] = useState<number | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const tags = value ?? [];

  const addTag = (raw: string) => {
    const v = raw.trim();
    if (!v) return;
    if (!tags.some((x) => x.toLowerCase() === v.toLowerCase())) onChange([...tags, v]);
    setInput('');
    setSelected(null);
  };

  const removeAt = (i: number) => {
    onChange(tags.filter((_, idx) => idx !== i));
    setSelected(null);
  };

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      addTag(input);
    } else if (e.key === 'Backspace' && input === '' && tags.length > 0) {
      e.preventDefault();
      removeAt(selected != null ? selected : tags.length - 1);
    }
  };

  if (readOnly) {
    return (
      <div className={`flex flex-wrap items-center gap-1 ${className}`}>
        {tags.map((tag, i) => (
          <span key={i} className="inline-flex items-center text-xs px-2 py-0.5 rounded border bg-muted border-border text-foreground">{tag}</span>
        ))}
      </div>
    );
  }

  return (
    <div
      onClick={() => inputRef.current?.focus()}
      className={`flex flex-wrap items-center gap-1 border border-input rounded px-2 py-1 bg-card min-h-[32px] cursor-text
        focus-within:ring-1 focus-within:ring-blue-400 ${className}`}
    >
      {tags.map((tag, i) => (
        <span
          key={i}
          onClick={(e) => { e.stopPropagation(); setSelected(selected === i ? null : i); }}
          className={`inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded border cursor-pointer transition-colors
            ${selected === i ? 'bg-blue-50 border-blue-400 text-blue-700' : 'bg-muted border-border text-foreground hover:border-blue-300'}`}
        >
          <span className="truncate max-w-[160px]">{tag}</span>
          <button
            type="button"
            onClick={(e) => { e.stopPropagation(); removeAt(i); }}
            className="text-muted-foreground hover:text-red-500 leading-none flex items-center"
            aria-label={`remove ${tag}`}
          >
            <IconClose size={10} />
          </button>
        </span>
      ))}
      <input
        ref={inputRef}
        type="text"
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={onKeyDown}
        onBlur={() => addTag(input)}
        placeholder={tags.length === 0 ? placeholder : ''}
        className="flex-1 min-w-[60px] text-sm bg-transparent text-foreground focus:outline-none py-0.5"
      />
    </div>
  );
}
