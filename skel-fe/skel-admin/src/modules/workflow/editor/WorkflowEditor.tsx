import React, { useCallback, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  ReactFlow, ReactFlowProvider, Background, Controls, MiniMap, addEdge,
  useNodesState, useEdgesState, useReactFlow,
  type Node, type Edge, type Connection,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import type { WorkflowGraf, DetectorSchema, DetectorConfig, WorkflowKind } from '../types';
import { KIND } from '../types';
import { idLabelStyle } from '../labels';
import { DetectorNode } from './DetectorNode';
import { ElementDetails } from './ElementDetails';
import { renderIcon, DEFAULT_SCHEMA_ICON, DEFAULT_CONFIG_ICON, DEFAULT_WF_SCHEMA_ICON, DEFAULT_WF_CONFIG_ICON } from '../../../components/IconPicker';
import { useWorkflowGrid } from '../../../settings/WorkflowGridContext';
import {
  grafToRF, rfToGraf, nodeToRF, nextNodeId, nextEdgeId, edgeStyle, edgeMarkerEnd, edgeRFType, readViewport, readGridSize, readSnapToGrid,
  type RFNodeData, type RFEdgeData,
} from './grafMapping';
import {
  IconPlus, IconTrash, IconReset, IconSave, IconClose, IconSearch, IconRefresh,
} from '../../../components/Icons';
import { statusChipStyle } from '../status';

export interface WorkflowEditorProps {
  /** title/name/icon/type shown in Panel 1 */
  id: number;
  title: string;
  name: string;
  icon?: string;
  kind: WorkflowKind;
  graf: WorkflowGraf;
  detectorSchemas: DetectorSchema[];
  detectorConfigs: DetectorConfig[];
  saving?: boolean;
  status?: string;                        // WorkflowConfig runtime status (shown in Panel 1)
  xid?: string;                           // WorkflowConfig engine runtime id (shown in Panel 1)
  detectorStatus?: Record<number, string>; // cid -> DetectorConfig status from /resolve (overlaid on nodes)
  resolving?: boolean;
  onResolve?: () => void;                 // fetch current engine state via /resolve (config only)
  onValidateCid?: (cid: number) => Promise<boolean>; // verify DetectorConfig exists before a cid change
  tracking?: boolean;                     // Track toggle state (auto-poll /resolve)
  pollCount?: number;                     // number of polls executed (shown on the button while tracking)
  freq?: number;                          // Track polling interval (ms)
  onFreqChange?: (ms: number) => void;
  onToggleTrack?: () => void;             // start/stop auto-polling
  onSave: (graf: WorkflowGraf) => void;
  onBack: () => void;
  onOpenDetails?: () => void; // open the WorkflowSchema/Config Details panel for editing
  onOpenDetectorSchema?: (id: number) => void;
  onOpenDetectorConfig?: (id: number) => void;
}

const EDGE_COLOR = '#64748b';

function WorkflowEditorInner(props: WorkflowEditorProps) {
  const { t } = useTranslation();
  const { id, name, icon, kind, graf, detectorSchemas, detectorConfigs, saving, status, xid, detectorStatus, resolving, onResolve, onValidateCid, tracking, pollCount = 0, freq = 3000, onFreqChange, onToggleTrack, onSave, onBack, onOpenDetails, onOpenDetectorSchema, onOpenDetectorConfig } = props;

  const initial = useMemo(() => grafToRF(graf), [graf]);
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<RFNodeData>>(initial.nodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge<RFEdgeData>>(initial.edges);

  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [paletteOpen, setPaletteOpen] = useState(false);

  const nodeTypes = useMemo(() => ({ detector: DetectorNode }), []);
  const addCounter = useRef(0);
  const { getViewport } = useReactFlow();
  const storedViewport = useMemo(() => readViewport(graf.meta), [graf]);

  // grid distance + snap-to-grid: per-workflow value (graf meta) overrides the global default setting
  const { gridSize: defaultGrid, snapToGrid: defaultSnap } = useWorkflowGrid();
  const [gridSize, setGridSize] = useState<number>(() => readGridSize(graf.meta) ?? defaultGrid);
  const [snapToGrid, setSnapToGrid] = useState<boolean>(() => readSnapToGrid(graf.meta) ?? defaultSnap);

  const onConnect = useCallback((c: Connection) => {
    setEdges((es) => {
      const id = nextEdgeId(es);
      const sourceHandle = c.sourceHandle ?? 'r';
      const targetHandle = c.targetHandle ?? 'l';
      const data: RFEdgeData = { label: '', color: EDGE_COLOR, lineStyle: 'solid', strokeWidth: 1.5, arrow: 'arrowclosed', edgeType: 'bezier', meta: { sourceHandle, targetHandle } };
      const edge: Edge<RFEdgeData> = {
        id: `e${id}`, source: c.source, target: c.target, sourceHandle, targetHandle,
        type: edgeRFType(data.edgeType),
        markerEnd: edgeMarkerEnd(data.arrow, data.color),
        style: edgeStyle(data),
        data,
      };
      return addEdge(edge, es);
    });
  }, [setEdges]);

  const addDetectorNode = useCallback((opts: { title: string; icon?: string; sid: number; cid?: number; tags?: string[]; desc?: string }) => {
    setNodes((ns) => {
      const id = nextNodeId(ns);
      const offset = (addCounter.current++ % 5) * 30;
      const rfNode = nodeToRF({
        id, title: opts.title, sid: opts.sid, cid: opts.cid, icon: opts.icon, tags: opts.tags, desc: opts.desc,
        meta: { pos_x: 80 + offset, pos_y: 80 + offset, size_width: 160, size_height: 64, color: 'white', border: '1px solid #94a3b8' },
        links: {},
      });
      return [...ns, rfNode];
    });
    setPaletteOpen(false);
  }, [setNodes]);

  const updateNode = useCallback((id: string, patch: Partial<RFNodeData>) => {
    setNodes((ns) => ns.map((n) => {
      if (n.id !== id) return n;
      const data = { ...n.data, ...patch } as RFNodeData;
      const next: Node<RFNodeData> = { ...n, data };
      if (patch.width !== undefined) next.width = patch.width;
      if (patch.height !== undefined) next.height = patch.height;
      return next;
    }));
  }, [setNodes]);

  const updateEdge = useCallback((id: string, patch: Partial<RFEdgeData>) => {
    setEdges((es) => es.map((e) => {
      if (e.id !== id) return e;
      const data = { ...(e.data as RFEdgeData), ...patch } as RFEdgeData;
      return {
        ...e, data,
        type: edgeRFType(data.edgeType),
        label: data.label || undefined,
        style: edgeStyle(data),
        markerEnd: edgeMarkerEnd(data.arrow, data.color),
      };
    }));
  }, [setEdges]);

  const deleteNode = useCallback((id: string) => {
    setNodes((ns) => ns.filter((n) => n.id !== id));
    setEdges((es) => es.filter((e) => e.source !== id && e.target !== id));
    setSelectedNodeId(null);
  }, [setNodes, setEdges]);

  const deleteEdge = useCallback((id: string) => {
    setEdges((es) => es.filter((e) => e.id !== id));
    setSelectedEdgeId(null);
  }, [setEdges]);

  const handleClear = useCallback(() => {
    setNodes([]); setEdges([]); setSelectedNodeId(null); setSelectedEdgeId(null);
  }, [setNodes, setEdges]);

  // persist a specific nodes/edges set (also captures pan/zoom + grid settings into the graf meta)
  const persist = useCallback((ns: Node<RFNodeData>[], es: Edge<RFEdgeData>[]) => {
    const g = rfToGraf(graf, ns, es);
    const vp = getViewport();
    g.meta = {
      ...(g.meta ?? {}),
      view_x: Math.round(vp.x), view_y: Math.round(vp.y), view_zoom: Number(vp.zoom.toFixed(3)),
      grid_size: gridSize, snap_to_grid: snapToGrid,
    };
    onSave(g);
  }, [onSave, graf, getViewport, gridSize, snapToGrid]);

  const handleSave = useCallback(() => persist(nodes, edges), [persist, nodes, edges]);

  // commit a node's cid (re-link its DetectorConfig) and persist via the API.
  // A non-empty cid is first verified against /detector/config/{cid}; if missing the change is
  // rejected (an error event is dispatched by onValidateCid) and false is returned.
  const updateCid = useCallback(async (id: string, cid: number | undefined): Promise<boolean> => {
    if (cid !== undefined && onValidateCid) {
      const ok = await onValidateCid(cid);
      if (!ok) return false;
    }
    const next = nodes.map((n) => (n.id === id ? { ...n, data: { ...n.data, cid } } : n));
    setNodes(next);
    persist(next, edges);
    return true;
  }, [nodes, edges, persist, setNodes, onValidateCid]);

  // search highlight (dim non-matching) + overlay DetectorConfig status (from /resolve) by node cid
  const displayNodes = useMemo(() => {
    const q = search.trim().toLowerCase();
    const ds = detectorStatus;
    if (!q && !ds) return nodes;
    return nodes.map((n) => {
      const cid = n.data.cid;
      const st = ds && cid !== undefined && cid !== null ? ds[cid] : undefined;
      const data = st !== undefined ? { ...n.data, status: st } : n.data;
      const style = q ? { ...n.style, opacity: n.data.title.toLowerCase().includes(q) ? 1 : 0.25 } : n.style;
      return { ...n, data, style };
    });
  }, [nodes, search, detectorStatus]);

  const selectedNode = nodes.find((n) => n.id === selectedNodeId) ?? null;
  const selectedEdge = edges.find((e) => e.id === selectedEdgeId) ?? null;

  return (
    // pin to the content area (below the 48px top bar, right of the 176px side nav) so the canvas
    // fills exactly - no page scroll and no empty space below it.
    <div className="fixed top-12 left-44 right-0 bottom-0 flex flex-col bg-card z-10">
      {/* Panel 1: identity */}
      <div className="flex items-center gap-3 px-4 py-2 border-b border-border bg-card shrink-0">
        <span className="inline-flex items-center justify-center w-7 h-7 shrink-0 text-foreground">
          {renderIcon(icon && icon.trim() ? icon : (kind === KIND.workflowConfig ? DEFAULT_WF_CONFIG_ICON : DEFAULT_WF_SCHEMA_ICON), 22)}
        </span>
        <div className="min-w-0 flex-1">
          {/* WorkflowConfig.name (NOT the schema-derived title), with the id label next to it */}
          <div className="flex items-center gap-1.5">
            <span className="text-sm text-foreground truncate">{name}</span>
            <span className="text-[10px] px-1.5 py-0.5 rounded shrink-0" style={idLabelStyle(kind)}>
              {id}
            </span>
            {/* WorkflowConfig runtime status label, next to the id */}
            {status ? (
              <span className="text-[10px] px-1.5 py-0.5 rounded shrink-0 font-semibold" style={statusChipStyle(status)} title={`status: ${status}`}>
                {status}
              </span>
            ) : null}
            <button onClick={() => onOpenDetails?.()} title={t('workflow.editor.openDetails')}
              className="shrink-0 inline-flex items-center justify-center w-[22px] h-[22px] rounded border border-border text-muted-foreground hover:bg-muted transition-colors text-base leading-none">
              …
            </button>
          </div>
          {/* xid row: engine runtime id (no name duplication) */}
          {kind === KIND.workflowConfig && xid ? (
            <div className="text-[11px] text-muted-foreground font-mono truncate" title={`xid: ${xid}`}>{xid}</div>
          ) : null}
        </div>
        <button onClick={onBack} className="text-muted-foreground hover:text-foreground p-1 rounded shrink-0" title={t('common.close')}>
          <IconClose size={18} />
        </button>
      </div>

      {/* Panel 2: toolbar - search first, then buttons */}
      <div className="flex items-center gap-2 px-4 py-2 border-b border-border bg-muted shrink-0 relative">
        <div className="flex items-center gap-1 border border-input rounded px-2 py-1 bg-card">
          <IconSearch size={13} className="text-muted-foreground" />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t('workflow.editor.search')}
            className="text-xs bg-transparent text-foreground focus:outline-none w-40" />
        </div>
        <div className="relative">
          <button onClick={() => setPaletteOpen((o) => !o)}
            className="btn-add">
            <IconPlus size={13} /> {t('common.add')}
          </button>
          {paletteOpen && (
            <DetectorPalette
              kind={kind}
              detectorSchemas={detectorSchemas}
              detectorConfigs={detectorConfigs}
              onPick={addDetectorNode}
              onClose={() => setPaletteOpen(false)}
            />
          )}
        </div>
        <button onClick={() => selectedNodeId && deleteNode(selectedNodeId)} disabled={!selectedNodeId}
          className="btn-danger disabled:opacity-40 disabled:cursor-not-allowed">
          <IconTrash size={13} /> {t('common.del')}
        </button>
        <button onClick={handleClear}
          className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-border text-muted-foreground hover:bg-card transition-colors">
          <IconReset size={13} /> {t('workflow.editor.clear')}
        </button>
        <button onClick={handleSave} disabled={saving}
          className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-green-500 text-green-700 hover:bg-green-50 disabled:opacity-40 transition-colors">
          <IconSave size={13} /> {saving ? t('common.saving') : t('common.save')}
        </button>
        {/* Resolve: fetch current engine state (WorkflowConfig + DetectorConfig statuses) - config only */}
        {kind === KIND.workflowConfig && onResolve && (
          <button onClick={onResolve} disabled={resolving}
            className="btn-design disabled:opacity-40 disabled:cursor-not-allowed">
            <IconRefresh size={13} /> {resolving ? t('workflow.resolving') : t('workflow.resolve')}
          </button>
        )}
        {/* Track toggle (auto-poll /resolve while pressed) + polling interval (ms) after it */}
        {kind === KIND.workflowConfig && onToggleTrack && (
          <>
            <button onClick={onToggleTrack} aria-pressed={!!tracking}
              className={`inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border transition-colors ${
                tracking ? 'bg-gray-600 border-gray-600 text-white' : 'border-gray-500 text-gray-600 hover:bg-gray-50'}`}>
              {/* while tracking, show the number of polls executed instead of the icon */}
              {tracking ? <span className="tabular-nums font-semibold">{pollCount}</span> : <IconRefresh size={13} />}
              {tracking ? t('workflow.tracking') : t('workflow.track')}
            </button>
            <input type="number" min={200} step={100} value={freq} title={`${t('workflow.freq')} (ms)`}
              onChange={(e) => onFreqChange?.(Math.max(200, Number(e.target.value) || freq))}
              className="w-16 text-xs field px-1.5 py-0.5 bg-card" />
          </>
        )}
        <div className="flex-1" />

        {/* per-workflow grid distance + snap-to-grid (persisted in the graf meta on Save) */}
        <label className="inline-flex items-center gap-1 text-xs text-muted-foreground cursor-pointer select-none">
          <input type="checkbox" checked={snapToGrid} onChange={(e) => setSnapToGrid(e.target.checked)} className="cursor-pointer" />
          {t('workflow.editor.snap')}
        </label>
        <div className="inline-flex items-center gap-1 text-xs text-muted-foreground">
          <span>{t('workflow.editor.grid')}</span>
          <input type="number" min={2} max={200} value={gridSize}
            onChange={(e) => setGridSize(Math.max(2, Number(e.target.value) || gridSize))}
            className="w-14 text-xs field px-1.5 py-0.5 bg-card" />
        </div>
      </div>

      {/* Canvas + element details */}
      <div className="flex-1 min-h-0 relative">
        <ReactFlow
          nodes={displayNodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          nodeTypes={nodeTypes}
          onNodeClick={(_, n) => { setSelectedNodeId(n.id); setSelectedEdgeId(null); }}
          onEdgeClick={(_, e) => { setSelectedEdgeId(e.id); setSelectedNodeId(null); }}
          onEdgeDoubleClick={(_, e) => deleteEdge(e.id)}
          onPaneClick={() => { setSelectedNodeId(null); setSelectedEdgeId(null); }}
          defaultViewport={storedViewport ?? undefined}
          fitView={!storedViewport}
          fitViewOptions={{ maxZoom: 1.6, padding: 0.2 }}
          minZoom={0.2}
          snapToGrid={snapToGrid}
          snapGrid={[gridSize, gridSize]}
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={gridSize} size={1} />
          <Controls />
          <MiniMap pannable zoomable />
        </ReactFlow>

        <ElementDetails
          node={selectedNode}
          edge={selectedEdge}
          onUpdateNode={updateNode}
          onUpdateEdge={updateEdge}
          onUpdateCid={updateCid}
          onDeleteNode={deleteNode}
          onDeleteEdge={deleteEdge}
          onOpenDetectorSchema={onOpenDetectorSchema}
          onOpenDetectorConfig={onOpenDetectorConfig}
          onClose={() => { setSelectedNodeId(null); setSelectedEdgeId(null); }}
        />
      </div>
    </div>
  );
}

interface PaletteProps {
  kind: WorkflowKind;
  detectorSchemas: DetectorSchema[];
  detectorConfigs: DetectorConfig[];
  onPick: (opts: { title: string; icon?: string; sid: number; cid?: number; tags?: string[]; desc?: string }) => void;
  onClose: () => void;
}

// A WorkflowSchema graph is built from DetectorSchema nodes; a WorkflowConfig graph from
// DetectorConfig nodes. Only the matching detector kind is offered in the palette.
function DetectorPalette({ kind, detectorSchemas, detectorConfigs, onPick, onClose }: PaletteProps) {
  const { t } = useTranslation();
  const [q, setQ] = useState('');
  const ql = q.trim().toLowerCase();
  const schemas = detectorSchemas.filter((d) => !ql || d.title.toLowerCase().includes(ql) || d.name.toLowerCase().includes(ql));
  const configs = detectorConfigs.filter((d) => !ql || d.name.toLowerCase().includes(ql));

  return (
    <>
      <div className="fixed inset-0 z-20" onClick={onClose} />
      <div className="absolute top-9 left-0 z-30 w-72 max-h-96 overflow-y-auto bg-card border border-border rounded shadow-xl p-2 space-y-2">
        <input autoFocus value={q} onChange={(e) => setQ(e.target.value)} placeholder={t('workflow.editor.searchDetectors')}
          className="w-full text-xs field px-2 py-1 bg-card" />

        {kind === KIND.workflowConfig ? (
          <div>
            {configs.length === 0 && <div className="text-xs text-muted-foreground px-1 py-1">{t('common.noData')}</div>}
            {configs.map((d) => (
              <button key={`c${d.id}`} onClick={() => onPick({ title: d.name, sid: d.schema?.id ?? -1, cid: d.id, tags: d.tags })}
                className="w-full text-left text-xs px-2 py-1 rounded hover:bg-muted flex items-center gap-2 text-foreground">
                <span className="shrink-0 inline-flex items-center justify-center w-[14px] h-[14px]">{renderIcon(DEFAULT_CONFIG_ICON, 14)}</span>
                <span className="truncate">{d.name} ({d.id})</span>
              </button>
            ))}
          </div>
        ) : (
          <div>
            {schemas.length === 0 && <div className="text-xs text-muted-foreground px-1 py-1">{t('common.noData')}</div>}
            {schemas.map((d) => (
              <button key={`s${d.id}`} onClick={() => onPick({ title: d.title || d.name, icon: d.icon, sid: d.id, tags: d.tags })}
                className="w-full text-left text-xs px-2 py-1 rounded hover:bg-muted flex items-center gap-2 text-foreground">
                <span className="shrink-0 inline-flex items-center justify-center w-[14px] h-[14px]">{renderIcon(d.icon && d.icon.trim() ? d.icon : DEFAULT_SCHEMA_ICON, 14)}</span>
                <span className="truncate">{d.name} ({d.id})</span>
              </button>
            ))}
          </div>
        )}
      </div>
    </>
  );
}

export function WorkflowEditor(props: WorkflowEditorProps) {
  return (
    <ReactFlowProvider>
      <WorkflowEditorInner {...props} />
    </ReactFlowProvider>
  );
}
