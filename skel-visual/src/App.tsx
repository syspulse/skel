import React, { useState } from 'react';
import ReactFlow, { Node, useNodesState, useEdgesState } from 'reactflow';
import CustomNode from './components/CustomNode';
import PropertiesPanel from './components/NodePropertyPanel';
import DiagramEditor from './components/DiagramEditor';


const nodeTypes = {
  custom: CustomNode,
};

function App() {
  const [nodes, setNodes, onNodesChange] = useNodesState([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState([]);
  const [selectedNode, setSelectedNode] = useState<Node | null>(null);

  return (
    <div style={{ display: 'flex', height: '100vh' }}>
      <DiagramEditor/>
      
    </div>
  );
}

export default App;