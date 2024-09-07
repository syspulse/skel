import React, { useState } from 'react';
import ReactFlow, { Node, useNodesState, useEdgesState } from 'reactflow';
import CustomNode from './components/CustomNode';
import PropertiesPanel from './components/NodePropertyPanel';
import DiagramEditor from './components/DiagramEditor';
import TopPanel from './components/TopPanel';

const nodeTypes = {
  custom: CustomNode,
};

function App() {
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);

  return (
    // <div style={{ display: 'flex', flexDirection: 'column', height: '100vh'}}>
    <div>
      {/* <TopPanel onLogin={handleLogin} />       */}
      <DiagramEditor/>
            
    </div>
  );
}

export default App;