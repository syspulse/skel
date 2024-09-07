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
  const [icon, setIcon] = useState('');
  // const [tags, setTags] = useState<string[]>([]);
  const [tags, setTags] = useState('');

  useEffect(() => {

    if (selectedNode) {
      setTitle(selectedNode.data.title || '');
      setDescription(selectedNode.data.description || '');
      setIcon(selectedNode.data.icon || '');
      setTags(selectedNode.data.tags || '');
    }
  }, [selectedNode]);


  const handleChange = (key: string, value: string) => {
    // console.log('handleChange', key, value, selectedNode);
    
    if (selectedNode) {      
      updateNode(selectedNode.id, { [key]: value });

      selectedNode.data[key] = value;

      if (key === 'title') setTitle(value);
      if (key === 'description') setDescription(value);
      if (key === 'icon') setIcon(value);
      if (key === 'tags') setTags(value);
    }
  }
  
  //if (!selectedNode) return <div className="panel-container"><h3 className="panel-title">Properties</h3></div>;
  if (!selectedNode) return null;


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
      <div>
        <label className="property-key">Icon:</label>
        <input
          className="property-value"
          type="text"
          value={icon}
          onChange={(e) => handleChange('icon', e.target.value)}
        />
      </div>
      <div>
        <label className="property-key">Tags:</label>
        <input
          className="property-value"
          type="text"
          value={tags}
          onChange={(e) => handleChange('tags', e.target.value)}
          placeholder="Enter tags separated by commas"
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
//export default PropertyPanel;