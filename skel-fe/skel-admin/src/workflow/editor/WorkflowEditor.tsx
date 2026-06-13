import React, { useCallback, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  ReactFlow, ReactFlowProvider, Background, Controls, MiniMap, addEdge,
  useNodesState, useEdgesState, useReactFlow,
  type Node, type Edge, type Connection,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import type { WorkflowGraf, DetectorSchema, DetectorConfig, EntityKind } from '../types';
import { DetectorNode } from './DetectorNode';
import { ElementDetails } from './ElementDetails';
import { renderIcon, DEFAULT_SCHEMA_ICON, DEFAULT_CONFIG_ICON } from './IconPicker';
import {
  grafToRF, rfToGraf, nodeToRF, nextNodeId, nextEdgeId, edgeStyle, edgeMarkerEnd, readViewport,
  type RFNodeData, type RFEdgeData,
} from './grafMapping';
import {
  IconPlus, IconTrash, IconReset, IconSave, IconClose, IconSearch, IconEdit,
} from '../../components/Icons';

export interface WorkflowEditorProps {
  /** title/name/icon/type shown in Panel 1 */
  title: string;
  name: string;
  icon?: string;
  kind: Exclude<EntityKind, 'detector-schema' | 'detector-config'>; // 'schema' | 'config'
  graf: WorkflowGraf;
  detectorSchemas: DetectorSchema[];
  detectorConfigs: DetectorConfig[];
  saving?: boolean;
  onSave: (graf: WorkflowGraf) => void;
  onBack: () => void;
  onOpenDetectorSchema?: (id: number) => void;
  onOpenDetectorConfig?: (id: number) => void;
}

const EDGE_COLOR = '#64748b';

function WorkflowEditorInner(props: WorkflowEditorProps) {
  const { t } = useTranslation();
  const { title, name, icon, kind, graf, detectorSchemas, detectorConfigs, saving, onSave, onBack, onOpenDetectorSchema, onOpenDetectorConfig } = props;

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

  const onConnect = useCallback((c: Connection) => {
    setEdges((es) => {
      const id = nextEdgeId(es);
      const sourceHandle = c.sourceHandle ?? 'r';
      const targetHandle = c.targetHandle ?? 'l';
      const data: RFEdgeData = { label: '', color: EDGE_COLOR, lineStyle: 'solid', strokeWidth: 1.5, arrow: 'arrowclosed', meta: { sourceHandle, targetHandle } };
      const edge: Edge<RFEdgeData> = {
        id: `e${id}`, source: c.source, target: c.target, sourceHandle, targetHandle,
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

  const handleSave = useCallback(() => {
    const g = rfToGraf(graf, nodes, edges);
    const vp = getViewport(); // persist current pan + zoom in the graf meta
    g.meta = { ...(g.meta ?? {}), view_x: Math.round(vp.x), view_y: Math.round(vp.y), view_zoom: Number(vp.zoom.toFixed(3)) };
    onSave(g);
  }, [onSave, graf, nodes, edges, getViewport]);

  // search highlight: dim non-matching nodes
  const displayNodes = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return nodes;
    return nodes.map((n) => ({
      ...n,
      style: { ...n.style, opacity: n.data.title.toLowerCase().includes(q) ? 1 : 0.25 },
    }));
  }, [nodes, search]);

  const selectedNode = nodes.find((n) => n.id === selectedNodeId) ?? null;
  const selectedEdge = edges.find((e) => e.id === selectedEdgeId) ?? null;

  return (
    // explicit height: react-flow needs a sized container (parent `main` only sets min-height)
    <div className="w-full flex flex-col" style={{ height: 'calc(100vh - 3rem)' }}>
      {/* Panel 1: identity */}
      <div className="flex items-center gap-3 px-4 py-2 border-b border-border bg-card shrink-0">
        <button onClick={onBack} className="text-muted-foreground hover:text-foreground p-1 rounded" title={t('common.close')}>
          <IconClose size={18} />
        </button>
        <span className="inline-flex items-center justify-center w-7 h-7">{renderIcon(icon, 22) ?? <IconEdit size={18} />}</span>
        <div className="min-w-0">
          <div className="text-sm text-foreground truncate">{title || name}</div>
          <div className="text-[11px] text-muted-foreground truncate">{name}</div>
        </div>
        <span className={`text-[10px] px-1.5 py-0.5 rounded ${kind === 'config' ? 'bg-emerald-100 text-emerald-700' : 'bg-blue-100 text-blue-700'}`}>
          {kind}
        </span>
      </div>

      {/* Panel 2: toolbar */}
      <div className="flex items-center gap-2 px-4 py-2 border-b border-border bg-muted shrink-0 relative">
        <div className="relative">
          <button onClick={() => setPaletteOpen((o) => !o)}
            className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-blue-500 text-blue-600 hover:bg-blue-50 transition-colors">
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
          className="inline-flex items-center gap-1.5 text-xs px-3 py-1 rounded border border-red-400 text-red-600 hover:bg-red-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors">
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
        <div className="flex-1" />
        <div className="flex items-center gap-1 border border-input rounded px-2 py-1 bg-card">
          <IconSearch size={13} className="text-muted-foreground" />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder={t('workflow.editor.search')}
            className="text-xs bg-transparent text-foreground focus:outline-none w-40" />
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
          proOptions={{ hideAttribution: true }}
        >
          <Background gap={12} size={1} />
          <Controls />
          <MiniMap pannable zoomable />
        </ReactFlow>

        <ElementDetails
          node={selectedNode}
          edge={selectedEdge}
          onUpdateNode={updateNode}
          onUpdateEdge={updateEdge}
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
  kind: 'schema' | 'config';
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
          className="w-full text-xs border border-input rounded px-2 py-1 bg-card text-foreground focus:outline-none focus:ring-1 focus:ring-blue-400" />

        {kind === 'config' ? (
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
