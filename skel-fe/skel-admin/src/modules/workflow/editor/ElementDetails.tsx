import React from 'react';
import { useTranslation } from 'react-i18next';
import type { Node, Edge } from '@xyflow/react';
import type { RFNodeData, RFEdgeData } from './grafMapping';
import { IconPicker } from '../../../components/IconPicker';
import { IconClose, IconTrash, IconArrowRight } from '../../../components/Icons';

interface ElementDetailsProps {
  node: Node<RFNodeData> | null;
  edge: Edge<RFEdgeData> | null;
  onUpdateNode: (id: string, patch: Partial<RFNodeData>) => void;
  onUpdateEdge: (id: string, patch: Partial<RFEdgeData>) => void;
  onDeleteNode: (id: string) => void;
  onDeleteEdge: (id: string) => void;
  onOpenDetectorSchema?: (id: number) => void;
  onOpenDetectorConfig?: (id: number) => void;
  onClose: () => void;
}

const inputCls = 'w-full text-sm field px-2 py-1 bg-card';

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-2">
      <label className="w-24 row-label">{label}</label>
      <div className="flex-1 min-w-0">{children}</div>
    </div>
  );
}

/** Read-only id row; with an optional [->] button to open the referenced detector's detail view. */
function IdRow({ label, value, onGo }: { label: string; value: React.ReactNode; onGo?: () => void }) {
  return (
    <div className="flex items-center gap-2">
      <label className="w-24 row-label">{label}</label>
      <div className="flex-1 text-sm font-mono text-foreground bg-muted border border-border rounded px-2 py-1 select-text">{value}</div>
      {onGo && (
        <button onClick={onGo} title="open details"
          className="shrink-0 p-1 rounded border border-border text-muted-foreground hover:text-blue-600 hover:bg-blue-50 transition-colors">
          <IconArrowRight size={14} />
        </button>
      )}
    </div>
  );
}

export function ElementDetails({ node, edge, onUpdateNode, onUpdateEdge, onDeleteNode, onDeleteEdge, onOpenDetectorSchema, onOpenDetectorConfig, onClose }: ElementDetailsProps) {
  const { t } = useTranslation();
  if (!node && !edge) return null;

  const isNode = !!node;
  const edgeId = edge ? Number(edge.id.replace(/^e/, '')) : 0;

  return (
    <div className="absolute top-0 right-0 bottom-0 w-[340px] max-w-[85%] bg-card border-l border-border z-20 flex flex-col shadow-2xl">
      <div className="flex items-center justify-between px-4 py-2.5 border-b border-border bg-muted gap-2">
        <h2 className="text-sm text-foreground flex-1">
          {isNode ? t('workflow.editor.nodeDetails') : t('workflow.editor.linkDetails')}
        </h2>
        <button
          onClick={() => (isNode ? onDeleteNode(node!.id) : onDeleteEdge(edge!.id))}
          className="inline-flex items-center gap-1 text-xs px-2 py-0.5 rounded border border-red-400 text-red-600 hover:bg-red-50 transition-colors">
          <IconTrash size={13} /> {t('common.delete')}
        </button>
        <button onClick={onClose} className="text-muted-foreground hover:text-foreground p-1 rounded" aria-label={t('common.close')}>
          <IconClose size={16} />
        </button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3">
        {node && (
          <>
            {/* identity always on top */}
            <IdRow label={t('workflow.fields.id')} value={node.id} />
            <IdRow label={t('workflow.fields.sid')} value={node.data.sid}
              onGo={onOpenDetectorSchema && node.data.sid >= 0 ? () => onOpenDetectorSchema(node.data.sid) : undefined} />
            {node.data.cid !== undefined && node.data.cid !== null && (
              <IdRow label={t('workflow.fields.cid')} value={node.data.cid}
                onGo={onOpenDetectorConfig ? () => onOpenDetectorConfig(node.data.cid as number) : undefined} />
            )}

            <Row label={t('workflow.editor.title')}>
              <input className={inputCls} value={node.data.title} onChange={(e) => onUpdateNode(node.id, { title: e.target.value })} />
            </Row>
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground">{t('workflow.editor.icon')}</label>
              <IconPicker value={node.data.icon} onChange={(icon) => onUpdateNode(node.id, { icon })} />
            </div>
            <Row label={t('workflow.editor.iconSize')}>
              <input type="number" min={8} max={128} className={inputCls}
                value={Math.round(node.data.iconSize)}
                onChange={(e) => onUpdateNode(node.id, { iconSize: Number(e.target.value) })} />
            </Row>
            <Row label={t('workflow.editor.iconPos')}>
              <div className="flex items-center gap-2">
                <input type="number" className={inputCls} title="x"
                  value={Math.round(node.data.iconX)} onChange={(e) => onUpdateNode(node.id, { iconX: Number(e.target.value) })} />
                <input type="number" className={inputCls} title="y"
                  value={Math.round(node.data.iconY)} onChange={(e) => onUpdateNode(node.id, { iconY: Number(e.target.value) })} />
              </div>
            </Row>
            <Row label={t('workflow.editor.iconBorder')}>
              <div className="flex items-center gap-2">
                <input type="color" value={toHex(borderColor(node.data.iconBorder), '#94a3b8')}
                  onChange={(e) => onUpdateNode(node.id, { iconBorder: `1px solid ${e.target.value}` })}
                  className="color-swatch" />
                <input className={inputCls} value={node.data.iconBorder} placeholder={t('workflow.editor.none')}
                  onChange={(e) => onUpdateNode(node.id, { iconBorder: e.target.value })} />
              </div>
            </Row>
            <Row label={t('workflow.editor.fontSize')}>
              <input type="number" min={6} max={48} className={inputCls}
                value={Math.round(node.data.fontSize)}
                onChange={(e) => onUpdateNode(node.id, { fontSize: Number(e.target.value) })} />
            </Row>
            <Row label={t('workflow.editor.fontColor')}>
              <div className="flex items-center gap-2">
                <input type="color" value={toHex(node.data.fontColor, '#1e293b')}
                  onChange={(e) => onUpdateNode(node.id, { fontColor: e.target.value })}
                  className="color-swatch" />
                <input className={inputCls} value={node.data.fontColor} onChange={(e) => onUpdateNode(node.id, { fontColor: e.target.value })} />
              </div>
            </Row>
            <Row label={t('workflow.editor.bgColor')}>
              <div className="flex items-center gap-2">
                <input type="color" value={toHex(node.data.color, '#ffffff')}
                  onChange={(e) => onUpdateNode(node.id, { color: e.target.value })}
                  className="color-swatch" />
                <input className={inputCls} value={node.data.color} onChange={(e) => onUpdateNode(node.id, { color: e.target.value })} />
              </div>
            </Row>
            <Row label={t('workflow.editor.borderColor')}>
              <div className="flex items-center gap-2">
                <input type="color" value={toHex(borderColor(node.data.border), '#94a3b8')}
                  onChange={(e) => onUpdateNode(node.id, { border: `1px solid ${e.target.value}` })}
                  className="color-swatch" />
                <input className={inputCls} value={node.data.border} onChange={(e) => onUpdateNode(node.id, { border: e.target.value })} />
              </div>
            </Row>
            {/* width / height: full-width rows so values up to 1000 are readable */}
            <Row label={t('workflow.editor.width')}>
              <input type="number" min={80} max={1000} className={inputCls}
                value={Math.round(node.width ?? node.data.width)}
                onChange={(e) => onUpdateNode(node.id, { width: Number(e.target.value) })} />
            </Row>
            <Row label={t('workflow.editor.height')}>
              <input type="number" min={40} max={1000} className={inputCls}
                value={Math.round(node.height ?? node.data.height)}
                onChange={(e) => onUpdateNode(node.id, { height: Number(e.target.value) })} />
            </Row>
          </>
        )}

        {edge && (
          <>
            <IdRow label={t('workflow.fields.id')} value={edgeId} />
            <IdRow label="from" value={edge.source} />
            <IdRow label="to" value={edge.target} />
            <Row label={t('workflow.fields.name')}>
              <input className={inputCls} value={edge.data?.label ?? ''} onChange={(e) => onUpdateEdge(edge.id, { label: e.target.value })} />
            </Row>
            <Row label={t('workflow.editor.edgeType')}>
              <select className={inputCls} value={edge.data?.edgeType ?? 'bezier'} onChange={(e) => onUpdateEdge(edge.id, { edgeType: e.target.value })}>
                <option value="straight">{t('workflow.editor.edgeStraight')}</option>
                <option value="step">{t('workflow.editor.edgeStep')}</option>
                <option value="smoothstep">{t('workflow.editor.edgeSmoothstep')}</option>
                <option value="bezier">{t('workflow.editor.edgeBezier')}</option>
              </select>
            </Row>
            <Row label={t('workflow.editor.lineStyle')}>
              <select className={inputCls} value={edge.data?.lineStyle ?? 'solid'} onChange={(e) => onUpdateEdge(edge.id, { lineStyle: e.target.value })}>
                <option value="solid">{t('workflow.editor.lineSolid')}</option>
                <option value="dashed">{t('workflow.editor.lineDashed')}</option>
                <option value="dotted">{t('workflow.editor.lineDotted')}</option>
              </select>
            </Row>
            <Row label={t('workflow.editor.boldness')}>
              <select className={inputCls} value={String(edge.data?.strokeWidth ?? 1.5)} onChange={(e) => onUpdateEdge(edge.id, { strokeWidth: Number(e.target.value) })}>
                <option value="1">{t('workflow.editor.thin')}</option>
                <option value="1.5">{t('workflow.editor.normal')}</option>
                <option value="2.5">{t('workflow.editor.bold')}</option>
                <option value="4">{t('workflow.editor.heavy')}</option>
              </select>
            </Row>
            <Row label={t('workflow.editor.arrow')}>
              <select className={inputCls} value={edge.data?.arrow ?? 'arrowclosed'} onChange={(e) => onUpdateEdge(edge.id, { arrow: e.target.value })}>
                <option value="arrowclosed">{t('workflow.editor.arrowSolid')}</option>
                <option value="arrow">{t('workflow.editor.arrowOpen')}</option>
                <option value="none">{t('workflow.editor.none')}</option>
              </select>
            </Row>
            <Row label={t('workflow.editor.edgeColor')}>
              <div className="flex items-center gap-2">
                <input type="color" value={toHex(edge.data?.color, '#64748b')}
                  onChange={(e) => onUpdateEdge(edge.id, { color: e.target.value })}
                  className="color-swatch" />
                <input className={inputCls} value={edge.data?.color ?? ''} onChange={(e) => onUpdateEdge(edge.id, { color: e.target.value })} />
              </div>
            </Row>
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
