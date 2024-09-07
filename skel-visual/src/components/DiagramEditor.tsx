import React, { useState, useCallback, useEffect, useRef } from 'react';
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
import PropertyPanel from './NodePropertyPanel';
import PropertyPanelProvider from './NodePropertyPanel';
import EdgePropertyPanel from './EdgePropertyPanel';
import EdgePropertyPanelProvider from './EdgePropertyPanel';
import TopPanel from './TopPanel';

const nodeTypes = {
  custom: CustomNode,
};

const initialNodes: Node[] = [
  {
    id: '0',
    type: 'custom',
    position: { x: 0, y: 0 },
    data: { 
      title: 'Deployer', 
      description: '0x946E9C780F3c79D80e51e68d259d0D7E794F2124', 
      icon: 'https://cryptologos.cc/logos/uniswap-uni-logo.png',
      tags: 'Uniswap',
      telemetry: {
        txCount: 0, 
        alertCount: 0,
        detectorCount: 0,
      },
    },
  },
  {
    id: '1',
    type: 'custom',
    position: { x: 300, y: 0 },
    data: { 
      title: 'Uniswap Router', 
      description: '0x3fC91A3afd70395Cd496C647d5a6CC9D4B2b7FAD', 
      icon: 'https://cryptologos.cc/logos/uniswap-uni-logo.png',
      tags: 'DEX,Uniswap',
      telemetry: {
        txCount: 0, 
        alertCount: 0, 
        detectorCount: 0,
      },
    },
  },
  {
    id: '2',
    type: 'custom',
    position: { x: 100, y: 200 },
    data: { 
      title: 'UNI', 
      description: '0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984', 
      icon: 'https://cryptologos.cc/logos/uniswap-uni-logo.png',
      tags: 'ERC20',
      // This node doesn't have telemetry, so counters won't be displayed      
    },
  },
];


const initialEdges: Edge[] = [
  { 
    id: 'e0-1', 
    source: '0', 
    target: '1',
    sourceHandle: 'right-source',
    targetHandle: 'left-target',
    markerEnd: { type: MarkerType.ArrowClosed },
    data: { transaction: '0x' },
    label: '0x',
  },
  { 
    id: 'e0-2', 
    source: '0', 
    target: '2',
    sourceHandle: 'bottom-source',
    targetHandle: 'top-target',
    markerEnd: { type: MarkerType.ArrowClosed },
    data: { transaction: '0x' },
    label: '0x',
  }
];

function DiagramEditor() {
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);  
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const [arrowSize, setArrowSize] = useState({ width: 4, height: 4 });
  //const { setViewport } = useReactFlow();
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<Edge | null>(null);

  const searchInputRef = useRef<HTMLInputElement | null>(null);

  const simulation = true;

  // ------------------------------------------------------------------------------- Simulation  
  useEffect(() => {
    if(!simulation) return;

    const interval = setInterval(() => {
      setNodes((nds) => 
        nds.map((node) => {
          // Randomly decide whether to update this node (1 in 3 chance)
          if (node.data.tags && node.data.tags.includes('Uniswap') && Math.random() < 0.33) {
            const newTxCount = (node.data.telemetry?.txCount || 0) + 1;
            return {
              ...node,
              data: {
                ...node.data,
                telemetry: {
                  ...node.data.telemetry,
                  txCount: newTxCount,
                },
              },
            };
          }
          return node;
        })
      );
    }, 500); // Update every 2 seconds

    // Cleanup interval on component unmount
    return () => clearInterval(interval);
  }, [setNodes]);
  
  //-----------------------------------------------------------------------------------------------


  // ------------------------------------------------------------------------ Keyboard ---
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === '/') {
        event.preventDefault(); // Prevent default action
        searchInputRef.current?.focus(); // Focus the search input
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, []);
  // -------------------------------------------------------------------------------------

  const onConnect = useCallback(
    (params: Connection) => setEdges((eds) => addEdge({ 
      ...params, 
      // label: '0x',
      // data: { transaction: '' },
      markerEnd: { type: MarkerType.ArrowClosed } 
    }, eds)),
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

  
  // const onSelectionChange = useCallback(({ nodes, edges }: OnSelectionChangeParams) => {
  //   console.log('Selection changed:', nodes, edges);
  //   setNodes((nds) =>
  //     nds.map((n) => ({
  //       ...n,
  //       data: {
  //         ...n.data,
  //         selected: nodes.some((selectedNode) => selectedNode.id === n.id),
  //       },
  //     }))
  //   );
  // }, [setNodes]);

  const onSelectionChange = useCallback(({ nodes, edges }: OnSelectionChangeParams) => {
    if (edges.length === 1) {
      setSelectedEdge(edges[0]);
      setSelectedNode(null);
    } else if (nodes.length === 1) {      
      setSelectedNode(nodes[0]);
      setSelectedEdge(null);
    } else {
      setSelectedNode(null);
      setSelectedEdge(null);
    }
  }, []);
  
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

  const updateEdge = useCallback((id: string, data: any) => {
    setEdges((eds) => eds.map((edge) => (edge.id === id ? { 
      ...edge, 
      label: data.transaction,
      data: { ...edge.data, ...data } 
    } : edge)));
  }, [setEdges]);

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
  
  const onSearch = useCallback((searchText: string) => {    
    const matchedNode = searchText.trim() === "" ? null : nodes.find(node => node.data.title.toLowerCase().startsWith(searchText.toLowerCase()));    
    if (matchedNode) {      
      setSelectedNode(matchedNode);
      console.log(">>>",matchedNode)
      
      //updateNode(matchedNode.id, { selected: true });
      matchedNode.selected = true
      
    } else {    
      setSelectedNode(null);
      nodes.forEach(node => 
        node.selected = false
        //updateNode(node.id, { selected: false })
      ); 
    }
  }, [nodes]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh'}}>
      
      <TopPanel 
        onLogin={() => console.log('Login clicked')}         
        onSearch={onSearch} 
        searchInputRef={searchInputRef}
      />
      
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

            snapToGrid={true}
            snapGrid={[15, 15]} 
          >          
            <Controls />
            <MiniMap />

            <Background variant={BackgroundVariant.Dots} gap={15} size={1} />
            
            
          </ReactFlow>
        </div>
        <PropertyPanelProvider selectedNode={selectedNode} updateNode={updateNode}/>
        <EdgePropertyPanelProvider selectedEdge={selectedEdge} updateEdge={updateEdge}/>
      </div>
    </div>
  );
}

export default DiagramEditor;