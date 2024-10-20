import React, { useEffect, useState } from 'react';
import { Edge, ReactFlowProvider } from 'reactflow';
import {truncateStr} from '../util/Util';

interface EdgePropertyPanelProps {
  selectedEdge: Edge | null;
  updateEdge: (id: string, key:string, value: any) => void;
  updateEdgeData: (id: string, key:string, value: any, data: any) => void;
}

function EdgePropertyPanel({ selectedEdge, updateEdge, updateEdgeData }: EdgePropertyPanelProps) {
  const [transaction, setTransaction] = useState('');
  const [animated, setAnimated] = useState(false);

  useEffect(() => {
    if (selectedEdge) {
      setTransaction(selectedEdge.data?.transaction || '');      
      setAnimated(selectedEdge.animated || false);
    }
  }, [selectedEdge]);

  const handleChange = (key: string, value: any) => {
    if (selectedEdge) {      
      if (key === 'label') {
        const label = truncateStr(value,6)
        updateEdgeData(selectedEdge.id, key, label, {'transaction':value} );
        setTransaction(value);
      }
      if (key === 'animated') {
        updateEdge(selectedEdge.id, key, value);
        setAnimated(value);
      }
    }
  };

  if (!selectedEdge) return null;

  return (
    <div className="panel-container">
      <h3 className="panel-title">Properties</h3>
      <div>
        <label className="property-key">Transaction:</label>
        <input
          className="property-value"
          type="text"
          value={transaction}
          onChange={(e) => handleChange('label', e.target.value)}
        />
      </div>
      <div style={{ display: 'flex', alignItems: 'center' }}> {/* Flex container for alignment */}
        <label className="property-key" style={{ marginTop: '4px' }}>Animated:</label>
        <input
          type="checkbox"
          checked={animated}
          onChange={(e) => handleChange('animated', e.target.checked)}
          style={{ marginLeft: '4px' }} // Add margin for spacing
        />
      </div>
    </div>
  );
}

function EdgePropertyPanelProvider(props:EdgePropertyPanelProps) {
    return (
      <ReactFlowProvider>
        <EdgePropertyPanel {...props} />
      </ReactFlowProvider>
    );
  }
  
export default EdgePropertyPanelProvider;
//export default EdgePropertyPanel;
