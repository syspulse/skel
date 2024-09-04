import React, { useEffect, useState } from 'react';
import { useReactFlow, Node, ReactFlowProvider, useNodesState } from 'reactflow';
import './DiagramEditor.css';
import CustomNode from './CustomNode';

interface PropertyPanelProps {
  selectedNode: Node | null;
  //nodes: Node[];
  updateNode: (id: string, data: any) => void;
}

function PropertyPanel({ selectedNode,updateNode }: PropertyPanelProps) {
  const { setNodes } = useReactFlow();  

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');

  useEffect(() => {

    if (selectedNode) {
      setTitle(selectedNode.data.title || '');
      setDescription(selectedNode.data.description || '');
    }
  }, [selectedNode]);


  const handleChange = (key: string, value: string) => {
    // console.log('handleChange', key, value, selectedNode);
    
    if (selectedNode) {
      updateNode(selectedNode.id, { [key]: value });

      selectedNode.data[key] = value;
      if (key === 'title') setTitle(value);
      if (key === 'description') setDescription(value);
    }
  }
  
  if (!selectedNode) return <div className="panel-container"><h3 className="panel-title">Properties</h3></div>;

  return (
    <div className="panel-container">
      <h3 className="panel-title">Properties</h3>
      <div>
        <label className="property-key">Title:</label>
        <input
          className="property-value"
          type="text"
          value={title}
          // value={selectedNode.data.title || ''}
          onChange={(e) => handleChange('title', e.target.value)}
        />
      </div>
      <div>
        <label className="property-key">Address:</label>
        <input
          className="property-value"
          type="text"
          value = {description}
          // value={selectedNode.data.description || ''}
          onChange={(e) => handleChange('description', e.target.value)}
        />
      </div>
    </div>
  );
}

function PropertyPanelProvider(props:PropertyPanelProps) {
  return (
    <ReactFlowProvider>
      <PropertyPanel {...props} />
    </ReactFlowProvider>
  );
}

export default PropertyPanelProvider;