import React from 'react';
import { Handle, Position, NodeResizer, type NodeProps } from '@xyflow/react';
import type { RFNodeData } from './grafMapping';
import { renderIcon } from '../../../components/IconPicker';
import { statusStyle } from '../status';
import { LABEL_SCHEMA, LABEL_CONFIG } from '../labels';

const HANDLE_STYLE: React.CSSProperties = {
  width: 8,
  height: 8,
  borderRadius: 0, // square connection points
  background: '#fff',
  border: '1px solid #475569',
};

const tagBase: React.CSSProperties = {
  fontSize: 9,
  lineHeight: '12px',
  padding: '0 4px',
  borderRadius: 3,
  whiteSpace: 'nowrap',
};
// sid -> schema (gray), cid -> config (default dark-gray/white); distinguished by color only
const sidTag: React.CSSProperties = { ...tagBase, ...LABEL_SCHEMA };
const cidTag: React.CSSProperties = { ...tagBase, ...LABEL_CONFIG };
// activity_id (runtime, from /resolve): amber so it stands apart from the id chips
const aidTag: React.CSSProperties = { ...tagBase, background: '#fcd34d', color: '#0f172a' };
// DetectorConfig.name label (top-left): smaller font than the id chips, light schema-style chip.
// A direct flex item (`flex: 0 1 auto` + min-width:0) so it takes its content width, shrinks with the
// node and truncates only when it overflows the space left of the id chips - and stays top-aligned.
const nameTag: React.CSSProperties = {
  ...tagBase, ...LABEL_SCHEMA, fontSize: 8,
  flex: '0 1 auto', minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis',
};
// long ids (e.g. UUID activity_id) are shown as first6…last6; the full value stays in the tooltip
const shortId = (s: string): string => (s.length > 32 ? `${s.slice(0, 6)}…${s.slice(-6)}` : s);

// A small id chip that copies its full value to the clipboard on click (hand cursor on hover).
// `nodrag` + stopPropagation keep the click from selecting/dragging the react-flow node.
function CopyChip({ style, title, display, value }: { style: React.CSSProperties; title: string; display: React.ReactNode; value: string }) {
  const [copied, setCopied] = React.useState(false);
  const copy = (e: React.MouseEvent) => {
    e.stopPropagation();
    try { navigator.clipboard?.writeText(value); } catch { /* clipboard unavailable */ }
    setCopied(true);
    window.setTimeout(() => setCopied(false), 450); // brief "copied" flash
  };
  // flash by brightening the SAME label color (no color change) to confirm the copy
  const flash: React.CSSProperties = copied ? { filter: 'brightness(1.35)' } : {};
  return (
    <span
      className="nodrag"
      style={{ ...style, cursor: 'pointer', transition: 'filter 120ms', ...flash }}
      title={`${title} (click to copy)`}
      onClick={copy}
      onMouseDown={(e) => e.stopPropagation()}
    >
      {display}
    </span>
  );
}

// Two target handles (top, left) and two source handles (bottom, right) give a visible
// input + output connection on all four sides. Edges connect source -> target.
export function DetectorNode({ data, selected }: NodeProps) {
  const d = data as RFNodeData;
  return (
    <div
      className="w-full h-full rounded shadow-sm relative"
      style={{
        background: d.color || 'white',
        border: selected ? '2px solid #3b82f6' : (d.border || '1px solid #94a3b8'),
        minWidth: 80,
        minHeight: 40,
      }}
    >
      <NodeResizer
        isVisible={selected}
        minWidth={80}
        minHeight={40}
        lineClassName="!border-blue-400"
        handleClassName="!bg-blue-400 !border-white"
      />

      {/* inputs */}
      <Handle id="t" type="target" position={Position.Top} style={HANDLE_STYLE} />
      <Handle id="l" type="target" position={Position.Left} style={HANDLE_STYLE} />

      {/* top row spanning the node: DetectorConfig.name (left, flexes with node width, truncates only
          when it overflows) + activity_id / sid / cid chips (right, fixed). Center-aligned so the
          smaller-font name lines up with the id chips. */}
      <div style={{ position: 'absolute', top: 2, left: 2, right: 2, display: 'flex', alignItems: 'center', gap: 4 }}>
        {d.detectorName ? <span style={nameTag} title={`DetectorConfig: ${d.detectorName}`}>{d.detectorName}</span> : null}
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 2, flexShrink: 0 }}>
          {d.activityId ? <CopyChip style={aidTag} title={`activity_id ${d.activityId}`} display={shortId(d.activityId)} value={d.activityId} /> : null}
          <CopyChip style={sidTag} title={`schema ${d.sid}`} display={d.sid} value={String(d.sid)} />
          {d.cid !== undefined && d.cid !== null && <CopyChip style={cidTag} title={`config ${d.cid}`} display={d.cid} value={String(d.cid)} />}
        </div>
      </div>

      {/* icon (top-left, configurable margin/size) + title beside it */}
      <div
        style={{
          position: 'absolute',
          top: d.iconY,
          left: d.iconX,
          right: 4,
          display: 'flex',
          alignItems: 'center',
          gap: 6,
          color: d.fontColor,
        }}
      >
        {d.icon ? (
          <span className="shrink-0 inline-flex items-center justify-center"
            style={{ width: d.iconSize, height: d.iconSize, border: d.iconBorder || undefined, borderRadius: d.iconBorder ? 3 : undefined, boxSizing: 'content-box' }}>
            {renderIcon(d.icon, d.iconSize)}
          </span>
        ) : null}
        <span className="truncate font-medium" style={{ fontSize: d.fontSize, color: d.fontColor }}>
          {d.title}
        </span>
      </div>

      {/* DetectorConfig status (current stored status, or live from /resolve): bottom-right, colored per Temporal */}
      {d.status ? (() => {
        const st = statusStyle(d.status);
        return (
          <div
            style={{
              position: 'absolute', bottom: 2, right: 2,
              ...tagBase,
              background: st.bg, color: st.fg, border: st.border ?? '1px solid rgba(0,0,0,0.15)',
              fontWeight: 600,
            }}
            title={`status: ${d.status}`}
          >
            {d.status}
          </div>
        );
      })() : null}

      {/* outputs */}
      <Handle id="r" type="source" position={Position.Right} style={HANDLE_STYLE} />
      <Handle id="b" type="source" position={Position.Bottom} style={HANDLE_STYLE} />
    </div>
  );
}
