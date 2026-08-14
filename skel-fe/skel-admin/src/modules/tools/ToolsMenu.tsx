import React, { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useTranslation } from 'react-i18next';
import { IconTools, IconBraces, IconClock } from '../../components/Icons';
import { JsonToolModal } from './JsonToolModal';

export function ToolsMenu() {
  const { t } = useTranslation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [jsonOpen, setJsonOpen] = useState(false);
  const [menuPos, setMenuPos] = useState<{ top: number; right: number } | null>(null);
  const btnRef = useRef<HTMLButtonElement>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const handler = (e: MouseEvent) => {
      const n = e.target as Node;
      if (btnRef.current?.contains(n) || dropdownRef.current?.contains(n)) return;
      setMenuOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, [menuOpen]);

  const toggleMenu = () => {
    if (menuOpen) { setMenuOpen(false); return; }
    const r = btnRef.current?.getBoundingClientRect();
    if (r) setMenuPos({ top: r.bottom + 4, right: window.innerWidth - r.right });
    setMenuOpen(true);
  };

  return (
    <>
      <button
        ref={btnRef}
        type="button"
        onClick={toggleMenu}
        className="relative text-header-fg-muted hover:text-header-fg p-1 rounded transition-colors"
        aria-label={t('tools.title')}
        aria-expanded={menuOpen}
        aria-haspopup="menu"
      >
        <IconTools size={18} />
      </button>

      {menuOpen && menuPos && createPortal(
        <div
          ref={dropdownRef}
          role="menu"
          style={{ top: menuPos.top, right: menuPos.right }}
          className="fixed min-w-[8rem] popover py-1 z-[80]"
        >
          <button
            type="button"
            role="menuitem"
            onClick={() => { setMenuOpen(false); setJsonOpen(true); }}
            className="menu-item"
          >
            <IconBraces size={14} className="text-muted-foreground shrink-0" />
            {t('tools.json')}
          </button>
          <button
            type="button"
            role="menuitem"
            disabled
            className="menu-item opacity-40 cursor-not-allowed"
          >
            <IconClock size={14} className="text-muted-foreground shrink-0" />
            {t('tools.time')}
          </button>
        </div>,
        document.body,
      )}

      {createPortal(
        <JsonToolModal open={jsonOpen} onClose={() => setJsonOpen(false)} />,
        document.body,
      )}
    </>
  );
}
