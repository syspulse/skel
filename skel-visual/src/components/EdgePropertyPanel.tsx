import React, { useEffect, useState } from 'react';
import { Edge, ReactFlowProvider } from 'reactflow';

interface EdgePropertyPanelProps {
  selectedEdge: Edge | null;
  updateEdge: (id: string, data: any) => void;
}

function EdgePropertyPanel({ selectedEdge, updateEdge }: EdgePropertyPanelProps) {
  const [transaction, setTransaction] = useState('');

  useEffect(() => {
    if (selectedEdge) {
      setTransaction(selectedEdge.data?.transaction || '');
    }
  }, [selectedEdge]);

  const handleChange = (key: string, value: any) => {
    if (selectedEdge) {
      updateEdge(selectedEdge.id, { [key]: value });
      if (key === 'transaction') setTransaction(value);
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
          onChange={(e) => handleChange('transaction', e.target.value)}
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
