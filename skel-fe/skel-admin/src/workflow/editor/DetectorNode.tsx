import React from 'react';
import { Handle, Position, NodeResizer, type NodeProps } from '@xyflow/react';
import type { RFNodeData } from './grafMapping';
import { renderIcon } from './IconPicker';

const HANDLE_STYLE: React.CSSProperties = {
  width: 8,
  height: 8,
  borderRadius: 0, // square connection points
  background: '#fff',
  border: '1px solid #475569',
};

const tagStyle: React.CSSProperties = {
  fontSize: 9,
  lineHeight: '12px',
  padding: '0 4px',
  borderRadius: 3,
  background: '#f1f5f9',
  color: '#64748b',
  whiteSpace: 'nowrap',
};

// Two target handles (top, left) and two source handles (bottom, right) give a visible
// input + output connection on all four sides. Edges connect source -> target.
export function DetectorNode({ data, selected }: NodeProps) {
  const d = data as RFNodeData;
  return (
    <div
      className="w-full h-full rounded shadow-sm relative overflow-hidden"
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

      {/* sid / cid tags: small, top-right corner, 2px margin */}
      <div style={{ position: 'absolute', top: 2, right: 2, display: 'flex', gap: 2 }}>
        <span style={tagStyle}>sid {d.sid}</span>
        {d.cid !== undefined && d.cid !== null && <span style={tagStyle}>cid {d.cid}</span>}
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
          <span className="shrink-0 inline-flex items-center justify-center" style={{ width: d.iconSize, height: d.iconSize }}>
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
