import React, { useState, useCallback } from 'react';
import ReactFlow, {
  Node,
  Edge,
  addEdge,
  Connection,
  MiniMap,
  Controls,
  Background,
  useNodesState,
  useEdgesState,
  BackgroundVariant,
  MarkerType,
  
  useReactFlow,
  NodeMouseHandler,
  ConnectionLineType,
  useOnSelectionChange,
  OnSelectionChangeParams,

} from 'reactflow';
import 'reactflow/dist/style.css';
import './DiagramEditor.css'; // Add this line

import CustomNode from './CustomNode';
import Sidebar from './Sidebar';
import PropertyPanel from './PropertyPanel';
import PropertyPanelProvider from './PropertyPanel';

const nodeTypes = {
  custom: CustomNode,
};

const initialNodes: Node[] = [
  {
    id: '1',
    type: 'custom',
    position: { x: 0, y: 0 },
    data: { 
      title: 'Uniswap Router', 
      description: '0x3fC91A3afd70395Cd496C647d5a6CC9D4B2b7FAD', 
      icon: 'https://cryptologos.cc/logos/uniswap-uni-logo.png'
    },
  },
  {
    id: '2',
    type: 'custom',
    position: { x: 250, y: 100 },
    data: { 
      title: 'UNI', 
      description: '0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984', 
      icon: 'https://cryptologos.cc/logos/uniswap-uni-logo.png'
    },
  },
];


const initialEdges: Edge[] = [
  { 
    id: 'e1-2', 
    source: '1', 
    target: '2',
    markerEnd: { type: MarkerType.ArrowClosed },
  }
];

function DiagramEditor() {
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);  
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const [arrowSize, setArrowSize] = useState({ width: 4, height: 4 });
  //const { setViewport } = useReactFlow();

  const onConnect = useCallback(
    (params: Connection) => setEdges((eds) => addEdge({ ...params, markerEnd: { type: MarkerType.ArrowClosed } }, eds)),
    [setEdges]
  );

  const onEdgeDoubleClick = useCallback((event: React.MouseEvent, edge: Edge) => {
    setEdges((eds) => eds.filter((e) => e.id !== edge.id));
  }, [setEdges]);

  const onAddNode = useCallback(() => {
    const newNode = {
      id: `${nodes.length + 1}`,
      type: 'custom',
      position: { 
        x: 0, 
        y: 0 
      },
      data: { 
        title: `Contract ${nodes.length + 1}`, 
        description: '0x000000000000000000000000000000001', 
        icon: '/assets/contract.png'
      },
    };
    setNodes((nds) => nds.concat(newNode));
  }, [nodes, setNodes]);

  const onNodesDelete = useCallback((deleted: Node[]) => {
    setEdges((eds) => eds.filter((edge) => 
      !deleted.some((node) => node.id === edge.source || node.id === edge.target)
    ));
  }, [setEdges]);

  // const onNodeClick: NodeMouseHandler = useCallback((event, node) => {
  //   console.log('onNodeClick ---------------------> ', event, node);
  //   setNodes((nds) =>
  //     nds.map((n) => ({
  //       ...n,
  //       data: {
  //         ...n.data,
  //         selected: n.id === node.id ? !n.data.selected : false,
  //       },
  //     }))
  //   );
  // }, [setNodes]);

  const onSelectionChange = useCallback(({ nodes, edges }: OnSelectionChangeParams) => {
    console.log('Selection changed:', nodes, edges);
    setNodes((nds) =>
      nds.map((n) => ({
        ...n,
        data: {
          ...n.data,
          selected: nodes.some((selectedNode) => selectedNode.id === n.id),
        },
      }))
    );
  }, [setNodes]);

  
  const onPaneClick = useCallback(() => {
    setNodes((nds) =>
      nds.map((n) => ({
        ...n,
        data: { ...n.data, selected: false },
      }))
    );
  }, [setNodes]);

  const updateNode = (id: string, data: any) => {
    setNodes((nds) =>
      nds.map((node) => (node.id === id ? { ...node, data: { ...node.data, ...data } } : node))
    );
  };

  const onSave = useCallback(() => {
    const flow = {
      nodes,
      edges,
      viewport: {
        x: 0,
        y: 0,
        zoom: 1,
      },
    };
    const json = JSON.stringify(flow);
    localStorage.setItem('flowState', json);
  }, [nodes, edges]);

  const onRestore = useCallback(() => {
    const json = localStorage.getItem('flowState');
    if (json) {
      const flow = JSON.parse(json);
      setNodes(flow.nodes || []);
      setEdges(flow.edges || []);
      //setViewport(flow.viewport);
    }
  }, [setNodes, setEdges]);

  const handleClearAll = () => {
    setNodes([]); // Assuming you're using a state setter to manage nodes
  };

  const onExport = useCallback(() => {
    const flow = { nodes, edges };
    const json = JSON.stringify(flow, null, 2);
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'flow-export.json';
    link.click();
    URL.revokeObjectURL(url);
  }, [nodes, edges]);
  
  const onImport = useCallback((file: File) => {
    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const json = JSON.parse(event.target?.result as string);
        setNodes(json.nodes || []);
        setEdges(json.edges || []);
      } catch (error) {
        console.error('Error parsing JSON:', error);
      }
    };
    reader.readAsText(file);
  }, [setNodes, setEdges]);
  

  return (
    <div style={{ display: 'flex', width: '100vw', height: '100vh' }}>
      <Sidebar 
        onAddNode={onAddNode} 
        onSave={onSave} 
        onRestore={onRestore}
        onClearAll={handleClearAll}
        onExport={onExport}
        onImport={onImport}
      /> 
      <div style={{ flex: 1, position: 'relative' }}>
        <ReactFlow
          nodes={nodes}
          edges={edges}
          
          onNodesChange={onNodesChange}
          onSelectionChange={onSelectionChange}

          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onEdgeDoubleClick={onEdgeDoubleClick}
          onNodesDelete={onNodesDelete}
          // onNodeClick={onNodeClick}
          onPaneClick={onPaneClick}

          nodeTypes={nodeTypes}
          fitView
          connectionRadius={20}
          // connectionLineType={ConnectionLineType.SmoothStep}          

          defaultEdgeOptions={{
            type: 'default',
            markerEnd: {
              type: MarkerType.ArrowClosed,
              width: arrowSize.width,
              height: arrowSize.height,
            },
          }}
        >          
          <Controls />
          <MiniMap />
          <Background variant={BackgroundVariant.Dots} gap={12} size={1} />
        </ReactFlow>
      </div>
      <PropertyPanelProvider selectedNode={nodes.find((node) => node.data.selected) || null} updateNode={updateNode}/>
    </div>
  );
}

export default DiagramEditor;