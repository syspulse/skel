import React from 'react';
import { useTranslation } from 'react-i18next';
import type { Node, Edge } from '@xyflow/react';
import type { RFNodeData, RFEdgeData } from './grafMapping';
import { IconPicker } from './IconPicker';
import { IconClose, IconTrash } from '../../components/Icons';

interface ElementDetailsProps {
  node: Node<RFNodeData> | null;
  edge: Edge<RFEdgeData> | null;
  onUpdateNode: (id: string, patch: Partial<RFNodeData>) => void;
  onUpdateEdge: (id: string, patch: Partial<RFEdgeData>) => void;
  onDeleteNode: (id: string) => void;
  onDeleteEdge: (id: string) => void;
  onClose: () => void;
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-2">
      <label className="w-24 shrink-0 text-xs text-muted-foreground">{label}</label>
      <div className="flex-1">{children}</div>
    </div>
  );
}

const inputCls = 'w-full text-sm border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400';

export function ElementDetails({ node, edge, onUpdateNode, onUpdateEdge, onDeleteNode, onDeleteEdge, onClose }: ElementDetailsProps) {
  const { t } = useTranslation();
  // Only mount the panel when a node or edge is actually selected (no stray panel otherwise).
  if (!node && !edge) return null;

  return (
    <div
      className="absolute top-0 right-0 bottom-0 w-[320px] max-w-[80%] bg-card border-l border-border z-20 flex flex-col shadow-2xl"
    >
      <div className="flex items-center justify-between px-4 py-2.5 border-b border-border bg-muted">
        <h2 className="text-sm text-foreground">
          {node ? t('workflow.editor.nodeDetails') : t('workflow.editor.linkDetails')}
        </h2>
        <button onClick={onClose} className="text-muted-foreground hover:text-foreground p-1 rounded" aria-label={t('common.close')}>
          <IconClose size={16} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
        {node && (
          <>
            <Row label={t('workflow.editor.title')}>
              <input className={inputCls} value={node.data.title}
                onChange={(e) => onUpdateNode(node.id, { title: e.target.value })} />
            </Row>
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground">{t('workflow.editor.icon')}</label>
              <IconPicker value={node.data.icon} onChange={(icon) => onUpdateNode(node.id, { icon })} />
            </div>
            <Row label={t('workflow.editor.bgColor')}>
              <div className="flex items-center gap-2">
                <input type="color" value={toHex(node.data.color, '#ffffff')}
                  onChange={(e) => onUpdateNode(node.id, { color: e.target.value })}
                  className="h-7 w-10 rounded border border-input bg-card cursor-pointer" />
                <input className={inputCls} value={node.data.color}
                  onChange={(e) => onUpdateNode(node.id, { color: e.target.value })} />
              </div>
            </Row>
            <Row label={t('workflow.editor.borderColor')}>
              <div className="flex items-center gap-2">
                <input type="color" value={toHex(borderColor(node.data.border), '#94a3b8')}
                  onChange={(e) => onUpdateNode(node.id, { border: `1px solid ${e.target.value}` })}
                  className="h-7 w-10 rounded border border-input bg-card cursor-pointer" />
                <input className={inputCls} value={node.data.border}
                  onChange={(e) => onUpdateNode(node.id, { border: e.target.value })} />
              </div>
            </Row>
            <div className="flex items-center gap-2">
              <Row label={t('workflow.editor.width')}>
                <input type="number" className={inputCls} value={Math.round(node.width ?? node.data.width)}
                  onChange={(e) => onUpdateNode(node.id, { width: Number(e.target.value) })} />
              </Row>
              <Row label={t('workflow.editor.height')}>
                <input type="number" className={inputCls} value={Math.round(node.height ?? node.data.height)}
                  onChange={(e) => onUpdateNode(node.id, { height: Number(e.target.value) })} />
              </Row>
            </div>
            <div className="text-[11px] text-muted-foreground space-y-0.5 pt-1">
              <div>sid: {node.data.sid}</div>
              {node.data.cid !== undefined && node.data.cid !== null && <div>cid: {node.data.cid}</div>}
            </div>
            <button onClick={() => onDeleteNode(node.id)}
              className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 transition-colors">
              <IconTrash size={13} /> {t('workflow.editor.deleteNode')}
            </button>
          </>
        )}

        {edge && (
          <>
            <Row label={t('workflow.editor.label')}>
              <input className={inputCls} value={edge.data?.label ?? ''}
                onChange={(e) => onUpdateEdge(edge.id, { label: e.target.value })} />
            </Row>
            <Row label={t('workflow.editor.edgeColor')}>
              <div className="flex items-center gap-2">
                <input type="color" value={toHex(edge.data?.color, '#64748b')}
                  onChange={(e) => onUpdateEdge(edge.id, { color: e.target.value })}
                  className="h-7 w-10 rounded border border-input bg-card cursor-pointer" />
                <input className={inputCls} value={edge.data?.color ?? ''}
                  onChange={(e) => onUpdateEdge(edge.id, { color: e.target.value })} />
              </div>
            </Row>
            <div className="text-[11px] text-muted-foreground">
              {edge.source} → {edge.target}
            </div>
            <button onClick={() => onDeleteEdge(edge.id)}
              className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 transition-colors">
              <IconTrash size={13} /> {t('workflow.editor.deleteLink')}
            </button>
          </>
        )}
      </div>
    </div>
  );
}

function toHex(c: string | undefined, def: string): string {
  if (!c) return def;
  const s = c.trim();
  if (/^#[0-9a-fA-F]{6}$/.test(s)) return s;
  const named: Record<string, string> = {
    white: '#ffffff', black: '#000000', red: '#ef4444', green: '#22c55e',
    blue: '#3b82f6', yellow: '#eab308', gray: '#94a3b8', grey: '#94a3b8',
  };
  return named[s.toLowerCase()] ?? def;
}

function borderColor(border: string | undefined): string | undefined {
  if (!border) return undefined;
  const m = border.match(/#[0-9a-fA-F]{6}|[a-zA-Z]+$/);
  return m ? m[0] : undefined;
}
