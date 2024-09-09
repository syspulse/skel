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
import {truncateAddr} from '../util/Util';

const nodeTypes = {
  custom: CustomNode,
};

const initialNodes: Node[] = [
  {
    id: '2134',
    type: 'custom',
    position: { x: 0, y: 0 },
    data: { 
      title: 'Deployer', 
      description: '0x946E9C780F3c79D80e51e68d259d0D7E794F2124', 
      icon: '/assets/key.png',
      tags: 'Uniswap',
      telemetry: {
        txCount: 0, 
        alertCount: 0,
        detectorCount: 0,
      },
    },
  },
  {
    id: '2135',
    type: 'custom',
    position: { x: 300, y: 0 },
    data: { 
      title: 'Universal Router', 
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
    id: '2136',
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
    source: '2134', 
    target: '2135',
    sourceHandle: 'right-source',
    targetHandle: 'left-target',
    markerEnd: { type: MarkerType.ArrowClosed },
    data: { transaction: '0x' },
    label: '0x',
    animated: true
  },
  { 
    id: 'e0-2', 
    source: '2134', 
    target: '2136',
    sourceHandle: 'bottom-source',
    targetHandle: 'top-target',
    markerEnd: { type: MarkerType.ArrowClosed },
    data: { transaction: '0x' },
    label: '0x',
  }
];

const tenantId = 645;

async function fetchDashboard(tenantId: number): Promise<any> {
  const url = `https://api.extractor.dev.hacken.cloud/api/v1/project/${tenantId}/dashboard`;
  const payload = `{"from":1725224400000,"to":1725814266025,"interval":"1d","timezone":"Europe/Kiev","id":${tenantId}}`
  
  try {
    const token = localStorage.getItem('jwtToken');
    
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      },
      body: payload//JSON.stringify(payload),      
    });

    console.log("response:",response);
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }

    const data = await response.json();
    const contracts = data.data.map((c:any) => ({"id":c.contract.id, "detectorCount":c.contract.count["SECURITY"] + c.contract.count["trigger"]}) )
    const txCount = data.transactions;

    return {"txCount":txCount,"contracts":contracts};

  } catch (error) {
    console.error('Error fetching simulation data:', error);
    return { "txCount": 0, "contracts": [] };
  }
}

function DiagramEditor() {
  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);  
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const [arrowSize, setArrowSize] = useState({ width: 4, height: 4 });
  //const { setViewport } = useReactFlow();
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<Edge | null>(null);

  const searchInputRef = useRef<HTMLInputElement | null>(null);

  const simulation = false;
  
  // ------------------------------------------------------------------------------- Simulation  
  useEffect(() => {
    
    const simulateData = setInterval(async () => {      
      console.log("Timer")
      if(simulation) {
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
      } else {
        udpateConters();        
      }
    }, 10000);

    // Cleanup interval on component unmount
    return () => clearInterval(simulateData);    

  }, [setNodes]);
  
  async function udpateConters() {
    const data = await fetchDashboard(tenantId);
    console.log('Dashboard data:', data);    
    setNodes((nds) => 
      nds.map((node) => {
        // find title match
        const found = data.contracts.filter((c:any) => c.id == node.id)
        
        if (found.length == 1) {          
          const newTxCount = data.txCount;
          const newDetectorCount = found[0].detectorCount
          return {
            ...node,
            data: {
              ...node.data,
              telemetry: {
                ...node.data.telemetry,
                txCount: newTxCount,
                detectorCount: newDetectorCount
              },
            },
          };
        }
        return node;
      })
    )
  }

  //-----------------------------------------------------------------------------------------------
  const onRefresh = useCallback(async () => {
    udpateConters();
    
  }, [setNodes]);

  const handleFetchDashboard = async () => {
    const data = await fetchDashboard(tenantId);
    console.log('Dashboard data:', data);    
  };  


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

  const updateNode = (id: string, key:String, value:any) => {
    setNodes((nds) =>
      nds.map((node) => (node.id === id ? { ...node, [key as string]:value } : node))
    );
  };

  const updateNodeData = useCallback((id: string, key:String, value:any, data: any) => {
    setNodes((nn) => nn.map((node) => {
      let n;
      if(node.id === id) {
        let n1;
        if(key || key != '') {
          n1 = { ... node, [key as string]: value};
        } else 
          n1 = node;

        if(data) {          
          n = { ...n1, data: { ...n1.data, ...data } };
        } else 
          n = n1;        

      } else n = node;
      return n;
    }));
  },[setNodes]);

  const updateEdgeData = useCallback((id: string, key:string, value: any, data: any) => {
    setEdges((eds) => eds.map((edge) => {        
      let e;
      if(edge.id === id) {
        let e1;
        if(key || key != '') {
          e1 = { ... edge, [key]: value};
        } else 
          e1 = edge;

        if(data) {
          e = { ...e1, data: { ...e1.data, ...data } };          
        } else 
          e = e1;

      } else e = edge;      
      return e;
    }));
  }, [setEdges]);

  const updateEdge = useCallback((id: string, key:string, value: any) => {
    setEdges((eds) => eds.map((edge) => (edge.id === id ? { 
      ...edge, 
      [key]: value
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

  const onExport = useCallback((file: File) => {
    
    const flow = { nodes, edges };
    const json = JSON.stringify(flow, null, 2);
    const blob = new Blob([json], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = file.name;//'flow-export.json';
    
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
            
      matchedNode.selected = true
      
    } else {    
      setSelectedNode(null);
      nodes.forEach(node => 
        node.selected = false        
      ); 
    }
  }, [nodes]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh'}}>
      
      <TopPanel 
        onLogin={() => console.log('Login')}         
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
          onRefresh={onRefresh}
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
        <PropertyPanelProvider selectedNode={selectedNode} updateNode={updateNode} updateNodeData={updateNodeData} />
        <EdgePropertyPanelProvider selectedEdge={selectedEdge} updateEdge={updateEdge} updateEdgeData={updateEdgeData}/>
      </div>
    </div>
  );
}

export default DiagramEditor;