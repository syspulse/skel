import React from 'react';
import { Handle, Position, NodeResizer, type NodeProps } from '@xyflow/react';
import type { RFNodeData } from './grafMapping';
import { renderIcon } from '../../../components/IconPicker';

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
// sid -> schema (blue), cid -> config (emerald); distinguished by color only (no prefix label)
const sidTag: React.CSSProperties = { ...tagBase, background: '#dbeafe', color: '#1d4ed8' };
const cidTag: React.CSSProperties = { ...tagBase, background: '#d1fae5', color: '#047857' };

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

      {/* sid / cid tags: small, top-right corner, 2px margin. No prefix - colour distinguishes them. */}
      <div style={{ position: 'absolute', top: 2, right: 2, display: 'flex', gap: 2 }}>
        <span style={sidTag} title={`schema #${d.sid}`}>{d.sid}</span>
        {d.cid !== undefined && d.cid !== null && <span style={cidTag} title={`config #${d.cid}`}>{d.cid}</span>}
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

      {/* outputs */}
      <Handle id="r" type="source" position={Position.Right} style={HANDLE_STYLE} />
      <Handle id="b" type="source" position={Position.Bottom} style={HANDLE_STYLE} />
    </div>
  );
}
