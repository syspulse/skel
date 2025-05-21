import React, { useState, useCallback, useEffect, useRef, RefObject } from 'react';
import { FileSaverOptions } from 'file-saver';
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
import NodePropertyPanelProvider from './NodePropertyPanel';
import EdgePropertyPanel from './EdgePropertyPanel';
import EdgePropertyPanelProvider from './EdgePropertyPanel';

import { initialNodes, initialEdges } from './defaultProject';
import { getDashboard } from '../extractor';
import Popup, { PopupLevel } from './Popup';

const nodeTypes = {
  custom: CustomNode,
};


async function loadDashboard(projectId: string): Promise<any> {
  const ts1 = Date.now();
  const ts0 = ts1 - 1000*60*60*24;
  const pid = projectId.split('-')[0];
  
  try {
    const data = await getDashboard(ts0,ts1,pid);
    const contracts = data.data.map((c:any) => {
      
      const severityCritical = c.severity.total["CRITICAL"] || 0;
      const severityHigh = c.severity.total["HIGH"] || 0;
      const severityMedium = c.severity.total["MEDIUM"] || 0;
      const severityLow = c.severity.total["LOW"] || 0;
      const severityInfo = c.severity.total["INFO"] || 0;
      return {
        "id":c.contract.id, 
        "name":c.contract.name,
        "network":c.contract.chainUid,
        "address":c.contract.address,
        "detectorCount":c.contract.count["SECURITY"] + c.contract.count["trigger"],
        "severityCritical":severityCritical,
        "severityHigh":severityHigh,
        "severityMedium":severityMedium,
        "severityLow":severityLow,
        "severityInfo":severityInfo
      }
    })
    const txCount = data.transactions;

    return {"txCount":txCount,"contracts":contracts};
  } catch (error) {
    console.error('failed to get dashboard:', error);    
    // return { "txCount": 0, "contracts": [] };
    return null;
  }
}

// =============================================================================================  
interface DiagramEditorProps {
  projectId: string;
  refreshFreq: number;
  searchText: string;
  searchInputRef: RefObject<HTMLInputElement>;
}

//function DiagramEditor() {
const DiagramEditor: React.FC<DiagramEditorProps> = ({ projectId, refreshFreq, searchText, searchInputRef }) => {

  const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes);  
  const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges);
  const [arrowSize, setArrowSize] = useState({ width: 4, height: 4 });
  //const { setViewport } = useReactFlow();
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);
  const [selectedEdge, setSelectedEdge] = useState<Edge | null>(null);
  
  //const searchInputRef = useRef<HTMLInputElement | null>(null);
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  
  const simulation = false;
  
  // ------------------------------------------------------------------------------- Simulation  
  useEffect(() => {
    
    const simulateData = async () => {       
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
    };

    if (timerRef.current) {
      clearInterval(timerRef.current);
    }

    timerRef.current = setInterval(simulateData, refreshFreq);

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };

  }, [setNodes,projectId, refreshFreq]);
  
  async function udpateConters() {    

    const data = await loadDashboard(projectId);
    console.log('Dashboard data:', data);    
    if(!data) {
      setPopup({ title: 'Telemetry', message: `failed to update telemetry`, level: 'error' });
      return;
    }

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
                detectorCount: newDetectorCount,
                severityCritical: found[0].severityCritical,
                severityHigh: found[0].severityHigh,
                severityMedium: found[0].severityMedium,
                severityLow: found[0].severityLow,
                severityInfo: found[0].severityInfo
              },
            },
          };
        }
        return node;
      })
    )
  }

  //-----------------------------------------------------------------------------------------------
  // const onRefresh = useCallback(async () => {
  //   udpateConters();
  // }, [setNodes]);

  const onRefresh = (async () => {
    udpateConters();
    setPopup({ title: 'Telemetry', message: `Refreshed: ${projectId}`, level: 'info' });
  });

  const onTopology = useCallback(async () => {
    try {
      const data = await loadDashboard(projectId);
      console.log('Dashboard data for population:', data);
  
      if (data.contracts && Array.isArray(data.contracts)) {
        const centerX = 500; // Adjust these values based on your canvas size
        const centerY = 300;
        const radius = 250; // Adjust the radius of the circle
  
        const newNodes = data.contracts.map((contract: any, index: number) => {
          let x, y;
          if (index === 0) {
            // Place the first contract in the center
            x = centerX;
            y = centerY;
          } else {
            // Place other contracts in a circle around the center
            const angle = ((index - 1) / (data.contracts.length - 1)) * 2 * Math.PI;
            x = centerX + radius * Math.cos(angle);
            y = centerY + radius * Math.sin(angle);
          }
  
          return {
            id: contract.id.toString(),
            type: 'custom',
            position: { x, y },
            data: {
              title: contract.name,
              description: contract.address,
              icon: '/assets/contract.png',
              network: contract.network,
              telemetry: {
                txCount: data.txCount,
                detectorCount: contract.detectorCount,
                severityCritical: contract.severityCritical,
                severityHigh: contract.severityHigh,
                severityMedium: contract.severityMedium,
                severityLow: contract.severityLow,
                severityInfo: contract.severityInfo
              },
            },
          };
        });
  
        setNodes(newNodes);
        setEdges([]); // Clear existing edges

        setPopup({ title: 'Topoplogy', message: `Topology: ${projectId}: contracts=${data.contracts.length}`, level: 'info' });
      } else {
        setPopup({ title: 'Topoplogy', message: `No contracts found`, level: 'error' });
        console.error('Invalid or empty contracts data');
      }
    } catch (error) {
      setPopup({ title: 'Topoplogy', message: `Error populating diagram: ${error}`, level: 'error' });
      console.error('Error populating diagram:', error);
    }
  }, [projectId, setNodes, setEdges]);

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

  const onAddGPTNode = useCallback(() => {
    const newNode = {
      id: `gpt-${Date.now()}`,
      type: 'custom',
      position: { x: 0, y: 0 },
      data: {
        nodeType: 'gpt',
        title: 'GPT Node',
        description: '',
        icon: '',
        tags: '',
        network: ''
      }
    };
    setNodes((nds) => nds.concat(newNode));
  }, [setNodes]);

  const onNodesDelete = useCallback((deleted: Node[]) => {
    setEdges((eds) => eds.filter((edge) => 
      !deleted.some((node) => node.id === edge.source || node.id === edge.target)
    ));
  }, [setEdges]);

  
  useEffect(() => {
    const matchedNode = searchText.trim() === '' ? null : nodes.find((node) => node.data.title.toLowerCase().startsWith(searchText.toLowerCase()));
    if (matchedNode) {
      setNodes((nds) =>
        nds.map((node) => ({
          ...node,
          selected: node.id === matchedNode.id,
        }))
      );
      setSelectedNode(matchedNode);
    } else {
      setNodes((nds) =>
        nds.map((node) => ({
          ...node,
          selected: false,
        }))
      );
      setSelectedNode(null);
    }
  }, [searchText, setNodes]);

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
    setSelectedNode(null);
    setSelectedEdge(null);
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

  const [popup, setPopup] = useState<{ title: string; message: string; level: PopupLevel } | null>(null);

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
    localStorage.setItem(`flowState_${projectId}`, json);
    setPopup({ title: 'Save', message: `Topology saved: ${projectId}`, level: 'info' });
  }, [nodes, edges, projectId]);

  const onRestore = useCallback(() => {    
    const json = localStorage.getItem(`flowState_${projectId}`);
    if (json) {
      const flow = JSON.parse(json);
      setNodes(flow.nodes || []);
      setEdges(flow.edges || []);
      setPopup({ title: 'Load', message: `Topology loaded: ${projectId}`, level: 'info' });
    } else {
      setPopup({ title: 'Load', message: `could not load: ${projectId}`, level: 'error' });
    }
  }, [setNodes, setEdges, projectId]);

  const handleClearAll = () => {
    setNodes([]); // Assuming you're using a state setter to manage nodes
    setPopup({ title: 'Clear', message: `Cleared: '${projectId}'`, level: 'info' });
  };

  const onImport = useCallback((file: File) => {
    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const json = JSON.parse(event.target?.result as string);
        setNodes(json.nodes || []);
        setEdges(json.edges || []);

        setPopup({ title: 'Import', message: `Imported: ${json.projectId}`, level: 'info' });
      } catch (error) {
        setPopup({ title: 'Import', message: `Failed to load: '${file.name}'`, level: 'error' });
        console.error('Failed to load:', error);
      }
    };
    reader.readAsText(file);
  }, [setNodes, setEdges, projectId]);
  
  const onExport = useCallback(async () => {
    const flow = { 
      projectId,
      nodes, 
      edges 
    };
    const json = JSON.stringify(flow, null, 2);
    const blob = new Blob([json], { type: 'application/json' });
  
    if ('showSaveFilePicker' in window) {
      try {
        const fileHandle = await (window as any).showSaveFilePicker({
          suggestedName: `project_${projectId}.json`,
          types: [{
            description: 'JSON Files',
            accept: { 'application/json': ['.json'] },
          }],
        });
  
        const writable = await fileHandle.createWritable();
        await writable.write(blob);
        await writable.close();
  
        setPopup({ title: 'Export', message: `exported: '${fileHandle.name}'`, level: 'info' });
        
      } catch (err) {
        if (err instanceof Error) {
          if (err.name !== 'AbortError') {
            setPopup({ title: 'Export', message: `Failed to save: '${projectId}': ${err.message}`, level: 'error' });
            console.error('Failed to save file:', err.message);
          }
        } else {
          const errorMessage = err instanceof Error ? err.toString() : 'An unknown error occurred';
          setPopup({ title: 'Export', message: `Failed to save: '${projectId}': ${errorMessage}`, level: 'error' });
          console.error(errorMessage);
        }
      }
    } else {
      // Fallback for browsers that don't support showSaveFilePicker
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `project_${projectId}.json`;
      link.click();
      URL.revokeObjectURL(url);
      setPopup({ title: 'Export', message: `exported: '${link.download}'`, level: 'info' });
    }
  }, [projectId, nodes, edges]);

  
  return (
    <div className="diagram-editor">
      <div className="diagram-content">
        <Sidebar 
          onAddNode={onAddNode}
          onAddGPTNode={onAddGPTNode}
          onSave={onSave} 
          onRestore={onRestore}
          onClearAll={handleClearAll}
          onExport={onExport}
          onImport={onImport}
          onRefresh={onRefresh}
          onTopology={onTopology}
          projectId={projectId}
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
        <NodePropertyPanelProvider selectedNode={selectedNode} updateNode={updateNode} updateNodeData={updateNodeData} />
        <EdgePropertyPanelProvider selectedEdge={selectedEdge} updateEdge={updateEdge} updateEdgeData={updateEdgeData}/>
      </div>
      {popup && (
        <Popup
          title={popup.title}
          message={popup.message}
          level={popup.level}
          onClose={() => setPopup(null)}
        />
      )}
    </div>
  );
}

export default DiagramEditor;