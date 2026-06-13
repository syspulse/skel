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

// Two target handles (top, left) and two source handles (bottom, right) give a visible
// input + output connection on all four sides. Edges connect source -> target.
export function DetectorNode({ data, selected }: NodeProps) {
  const d = data as RFNodeData;
  return (
    <div
      className="w-full h-full rounded shadow-sm flex flex-col items-center justify-center text-center px-2"
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

      <div className="flex items-center gap-1.5 max-w-full">
        {d.icon ? <span className="shrink-0">{renderIcon(d.icon, 16)}</span> : null}
        <span className="text-xs font-medium text-slate-800 truncate">{d.title}</span>
      </div>
      {d.cid !== undefined && d.cid !== null ? (
        <span className="text-[9px] text-slate-500 mt-0.5">config #{d.cid}</span>
      ) : (
        <span className="text-[9px] text-slate-400 mt-0.5">schema #{d.sid}</span>
      )}

      {/* outputs */}
      <Handle id="r" type="source" position={Position.Right} style={HANDLE_STYLE} />
      <Handle id="b" type="source" position={Position.Bottom} style={HANDLE_STYLE} />
    </div>
  );
}
