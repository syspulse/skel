import React, { useEffect, useState } from 'react';
import { useReactFlow, Node, ReactFlowProvider, useNodesState } from 'reactflow';
import './DiagramEditor.css';
import CustomNode from './CustomNode';
import IconSelector from './IconSelector';
import NetworkDropdown from './NetworkDropdown';

interface PropertyPanelProps {
  selectedNode: Node | null;
  //nodes: Node[];
  updateNode: (id: string, key:String, value: any) => void;
  updateNodeData: (id: string, key:String, value:any, data: any) => void;
}

function NodePropertyPanel({ selectedNode,updateNode,updateNodeData }: PropertyPanelProps) {
  const { setNodes } = useReactFlow();  

  const [id, setId] = useState('');
  const [selected, setSelected] = useState(false);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [icon, setIcon] = useState('');
  // const [tags, setTags] = useState<string[]>([]);
  const [tags, setTags] = useState('');
  const [isNetworkDropdownOpen, setIsNetworkDropdownOpen] = useState(false);

  useEffect(() => {

    if (selectedNode) {
      setId(selectedNode.id || '');
      setSelected(selectedNode.selected || false);

      setTitle(selectedNode.data.title || '');
      setDescription(selectedNode.data.description || '');
      setIcon(selectedNode.data.icon || '');
      setTags(selectedNode.data.tags || '');
    }
  }, [selectedNode]);


  const handleChange = (key: string, value: string) => {    
    
    if (selectedNode) {      
      //updateNode(selectedNode.id, { [key]: value });
      // updateNode(selectedNode.id, key, value );
      // selectedNode.data[key] = value;
      
      switch (key) {
        case 'id':
          updateNode(selectedNode.id, "id", value);      
          setId(value);
          break;
        case 'title':
          updateNodeData(selectedNode.id, "", null, { [key]: value });
          selectedNode.data[key] = value;
          setTitle(value);
          break;
        case 'description':
          updateNodeData(selectedNode.id, "", null, { [key]: value });
          selectedNode.data[key] = value;
          setDescription(value);
          break;
        case 'icon':
          updateNodeData(selectedNode.id, "", null, { [key]: value });
          selectedNode.data[key] = value;
          setIcon(value);
          break;
        case 'tags':
          updateNodeData(selectedNode.id, "", null, { [key]: value });
          selectedNode.data[key] = value;
          setTags(value);
          break;
        case 'network':
          updateNodeData(selectedNode.id, "", null, { [key]: value });
          break;
        default:
          break;
      }
            
    }
  }
  
  const handleIconSelect = (newIcon: string) => {
    handleChange('icon', newIcon);
  };

  const handleNetworkSelect = (networkId: string) => {
    handleChange('network', networkId);
    setIsNetworkDropdownOpen(false);
  };

  //if (!selectedNode) return <div className="panel-container"><h3 className="panel-title">Properties</h3></div>;
  if (!selectedNode) return null;


  return (
    <div className="panel-container">
      <h3 className="panel-title">Properties</h3>
      
      <div>
        <label className="property-key">id:</label>
        <input
          className="property-value"
          type="text"
          value={id}
          // readOnly
          onChange={(e) => handleChange('id', e.target.value)}
        />
      </div>
      <div>
        <label className="property-key">selected:</label>
        <input
          className="property-value-readonly"
          type="text"
          value={selected ? 'true' : 'false'}
          readOnly
        />
      </div>


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
      
      <IconSelector
        selectedIcon={icon}
        onSelectIcon={handleIconSelect}
      />

      <div>
        <label className="property-key">Tags:</label>
        <input
          className="property-value"
          type="text"
          value={tags}
          onChange={(e) => handleChange('tags', e.target.value)}
          placeholder="separated by commas"
        />
      </div>

      <div>
        <label className="property-key">Network:</label>
        <NetworkDropdown
          selectedNetwork={selectedNode.data.network || ''}
          onNetworkChange={handleNetworkSelect}
        />
      </div>      
      
    </div>
  );
}

function NodePropertyPanelProvider(props:PropertyPanelProps) {
  return (
    <ReactFlowProvider>
      <NodePropertyPanel {...props} />
    </ReactFlowProvider>
  );
}

export default NodePropertyPanelProvider;
//export default PropertyPanel;