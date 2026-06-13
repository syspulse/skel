import type { Node, Edge } from '@xyflow/react';
import { MarkerType } from '@xyflow/react';
import type { WorkflowGraf, WorkflowNode, WorkflowLink, Meta } from '../types';

// react-flow node data we attach to each DetectorNode.
export interface RFNodeData extends Record<string, unknown> {
  title: string;
  icon?: string;
  sid: number;
  cid?: number;
  tags?: string[];
  desc?: string;
  color: string;        // background color (meta.color)
  border: string;       // border style (meta.border)
  width: number;
  height: number;
  iconSize: number;     // meta.icon_size
  iconX: number;        // meta.icon_x (left margin)
  iconY: number;        // meta.icon_y (top margin)
  fontSize: number;     // meta.font_size (title)
  fontColor: string;    // meta.font_color (title)
  meta: Meta;           // full original meta (preserved on save)
}

export interface RFEdgeData extends Record<string, unknown> {
  label: string;
  color: string;        // stroke color (meta.color)
  meta: Meta;
}

const DEF_W = 160;
const DEF_H = 64;
const DEF_NODE_COLOR = 'white';
const DEF_NODE_BORDER = '1px solid #94a3b8';
const DEF_EDGE_COLOR = '#64748b';
const DEF_ICON_SIZE = 18;
const DEF_ICON_X = 10;
const DEF_ICON_Y = 10;
const DEF_FONT_SIZE = 13;
const DEF_FONT_COLOR = '#1e293b';

function num(meta: Meta | undefined, key: string, def: number): number {
  const v = meta?.[key];
  if (typeof v === 'number') return v;
  if (typeof v === 'string' && v.trim() !== '' && !isNaN(Number(v))) return Number(v);
  return def;
}
function str(meta: Meta | undefined, key: string, def: string): string {
  const v = meta?.[key];
  return typeof v === 'string' && v.trim() ? v : def;
}

export function nodeToRF(n: WorkflowNode): Node<RFNodeData> {
  const meta = n.meta ?? {};
  const width = num(meta, 'size_width', DEF_W);
  const height = num(meta, 'size_height', DEF_H);
  return {
    id: String(n.id),
    type: 'detector',
    position: { x: num(meta, 'pos_x', 0), y: num(meta, 'pos_y', 0) },
    width,
    height,
    data: {
      title: n.title,
      icon: n.icon,
      sid: n.sid,
      cid: n.cid,
      tags: n.tags,
      desc: n.desc,
      color: str(meta, 'color', DEF_NODE_COLOR),
      border: str(meta, 'border', DEF_NODE_BORDER),
      width,
      height,
      iconSize: num(meta, 'icon_size', DEF_ICON_SIZE),
      iconX: num(meta, 'icon_x', DEF_ICON_X),
      iconY: num(meta, 'icon_y', DEF_ICON_Y),
      fontSize: num(meta, 'font_size', DEF_FONT_SIZE),
      fontColor: str(meta, 'font_color', DEF_FONT_COLOR),
      meta,
    },
  };
}

export function linkToRF(l: WorkflowLink): Edge<RFEdgeData> {
  const meta = l.meta ?? {};
  const color = str(meta, 'color', DEF_EDGE_COLOR);
  const label = str(meta, 'label', l.typ ?? '');
  // default: source from the node's RIGHT connector -> destination LEFT connector
  const sourceHandle = str(meta, 'sourceHandle', 'r');
  const targetHandle = str(meta, 'targetHandle', 'l');
  return {
    id: `e${l.id}`,
    source: String(l.from),
    target: String(l.to),
    sourceHandle,
    targetHandle,
    label: label || undefined,
    markerEnd: { type: MarkerType.ArrowClosed, color },
    style: { stroke: color },
    data: { label, color, meta },
  };
}

export function grafToRF(graf: WorkflowGraf): { nodes: Node<RFNodeData>[]; edges: Edge<RFEdgeData>[] } {
  const nodes = Object.values(graf.nodes ?? {}).map(nodeToRF);
  const edges = Object.values(graf.links ?? {}).map(linkToRF);
  autoLayoutIfOverlapping(nodes);
  return { nodes, edges };
}

// Fallback for graphs whose nodes share a position (e.g. older data all at 0,0): spread them
// into a grid so they are never stacked invisibly. Existing distinct positions are preserved.
function autoLayoutIfOverlapping(nodes: Node<RFNodeData>[]): void {
  if (nodes.length < 2) return;
  const seen = new Set<string>();
  let overlapping = false;
  for (const n of nodes) {
    const key = `${Math.round(n.position.x)},${Math.round(n.position.y)}`;
    if (seen.has(key)) { overlapping = true; break; }
    seen.add(key);
  }
  if (!overlapping) return;
  const COLS = 4;
  nodes.forEach((n, i) => {
    n.position = { x: 60 + (i % COLS) * 220, y: 80 + Math.floor(i / COLS) * 140 };
  });
}

function edgeIdNum(edgeId: string): number {
  const n = Number(edgeId.replace(/^e/, ''));
  return isNaN(n) ? 0 : n;
}

// Rebuild a WorkflowGraf from the current react-flow nodes/edges, preserving ids and
// persisting visual topology (position/size/color) into each element's `meta`.
export function rfToGraf(
  base: WorkflowGraf,
  nodes: Node<RFNodeData>[],
  edges: Edge<RFEdgeData>[],
): WorkflowGraf {
  const wfNodes: Record<string, WorkflowNode> = {};
  for (const rn of nodes) {
    const id = Number(rn.id);
    const d = rn.data;
    const w = rn.width ?? d.width ?? DEF_W;
    const h = rn.height ?? d.height ?? DEF_H;
    const meta: Meta = {
      ...(d.meta ?? {}),
      pos_x: Math.round(rn.position.x),
      pos_y: Math.round(rn.position.y),
      size_width: Math.round(w),
      size_height: Math.round(h),
      color: d.color,
      border: d.border,
      icon_size: d.iconSize,
      icon_x: d.iconX,
      icon_y: d.iconY,
      font_size: d.fontSize,
      font_color: d.fontColor,
    };
    wfNodes[String(id)] = {
      id,
      title: d.title,
      sid: d.sid,
      cid: d.cid,
      icon: d.icon,
      tags: d.tags,
      desc: d.desc,
      meta,
      links: {},
    };
  }

  const wfLinks: Record<string, WorkflowLink> = {};
  for (const re of edges) {
    const id = edgeIdNum(re.id);
    const d = re.data;
    const meta: Meta = {
      ...(d?.meta ?? {}),
      color: d?.color ?? DEF_EDGE_COLOR,
      label: d?.label ?? '',
      sourceHandle: re.sourceHandle ?? 'r',
      targetHandle: re.targetHandle ?? 'l',
    };
    wfLinks[String(id)] = {
      id,
      from: Number(re.source),
      to: Number(re.target),
      typ: d?.label || undefined,
      meta,
    };
  }

  // keep node.links in sync with links (mirror backend WorkflowGraf.sync)
  for (const l of Object.values(wfLinks)) {
    for (const nid of [l.from, l.to]) {
      const n = wfNodes[String(nid)];
      if (n) n.links = { ...(n.links ?? {}), [String(l.id)]: l };
    }
  }

  return { ...base, nodes: wfNodes, links: wfLinks };
}

export function nextNodeId(nodes: Node<RFNodeData>[]): number {
  return nodes.reduce((mx, n) => Math.max(mx, Number(n.id)), -1) + 1;
}
export function nextEdgeId(edges: Edge<RFEdgeData>[]): number {
  return edges.reduce((mx, e) => Math.max(mx, edgeIdNum(e.id)), -1) + 1;
}
