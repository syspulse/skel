import React, { memo, useCallback, useEffect, useRef } from 'react';
import { Handle, Position, NodeProps, useReactFlow, Node } from 'reactflow';
import {truncateAddr} from '../util/Util';
import { networksMap } from '../Network';

interface CustomNodeData {
  title: string;
  description: string;
  icon: string;
  // selected: boolean;
  extraHandles?: { type: 'source' | 'target',  position: Position}[];
  tags: string;
  network: string;

  telemetry?: {
    txCount: number;
    alertCount: number;
    detectorCount: number;
    severityCritical: number;
    severityHigh: number;
    severityMedium: number;
    severityLow: number;
    severityInfo: number;
  };
}

function CustomNode({ data, id, selected }: NodeProps<CustomNodeData>) {
  const { deleteElements, setNodes } = useReactFlow();

  // --------------------------------------------------------------- Visual Effect only
  const txCountRef = useRef<HTMLSpanElement>(null);
  var counter = -1;
  useEffect(() => {    
    if (data.telemetry?.txCount !== counter && txCountRef.current) {
      txCountRef.current.classList.add('blink');
      setTimeout(() => txCountRef.current?.classList.remove('blink'), 1000);
      counter = data.telemetry?.txCount || 0;
    }
  }, [data.telemetry?.txCount, id]);
  // --------------------------------------------------------------- Visual Effect only

  const onDelete = () => {
    deleteElements({ nodes: [{ id }] });
  };

  const onDoubleClick = useCallback((event: React.MouseEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width) * 100;
    const y = ((event.clientY - rect.top) / rect.height) * 100;
    console.log('onDoubleClick', x, y);

    setNodes((nds: Node<CustomNodeData>[]) =>
      nds.map((node) =>
        node.id === id
          ? {
              ...node,
              data: {
                ...node.data,
                extraHandles: [
                  ...(node.data.extraHandles || []),
                  { 
                    type: 'source', 
                    position: Position.Top,
                    id: `extra-target-${Date.now()}`
                  }
                ]
              }
            }
          : node
      )
    );
    
  }, [id, setNodes]);

  const network = networksMap.get(data.network);  

  return (
    <div>
      
      <button className="delete-button" onClick={onDelete}>×</button>
      {network && (
        <img 
          src={network.icon} 
          alt={network.name} 
          className="custom-node-network-icon"
          title={network.name}
        />
      )}
      <span className="custom-node-id-label">{id}</span>
      
           
      <Handle type="target" position={Position.Top} id="top-target" className="target-handle" />
      <Handle type="source" position={Position.Top} id="top-source" className="source-handle" />
      
      <Handle type="target" position={Position.Left} id="left-target" className="target-handle" />
      <Handle type="source" position={Position.Left} id="left-source" className="source-handle"  />
      
      <Handle type="target" position={Position.Right} id="right-target" className="target-handle" />
      <Handle type="source" position={Position.Right} id="right-source" className="source-handle" />
      
      <Handle type="target" position={Position.Bottom} id="bottom-target" className="target-handle" />
      <Handle type="source" position={Position.Bottom} id="bottom-source" className="source-handle" />

      {data.extraHandles?.map((handle, index) => (        
        <Handle type="source" position={Position.Top} id="top-source-10" className="source-handle" style={{transform:'translate(+50%, 0%)'}} />
      ))}
      
      <div style={{ display: 'flex', alignItems: 'center' }}>
        {/* <span style={{ marginRight: '10px', fontSize: '24px' }}>{data.icon}</span> */}
        {/* <div className="custom-node-icon">{data.icon}</div> */}

        <img 
          src={data.icon} 
          // alt={data.title} 
          className="custom-node-icon"          
        />

        <div>                    
          {/* <p style={{ margin: '5px 0 0' }}>{data.description}</p> */}
          <div className="custom-node-title">
            {data.title}
          </div>

          {data.tags && (
            <div className="custom-node-tags">
              {data.tags.split(',').map((tag, index) => (
                <span key={index} className="custom-node-tag">{tag}</span>
              ))}
            </div>
          )}
          <a 
            href={`https://etherscan.io/address/${data.description}`} 
            target="_blank" 
            rel="noopener noreferrer"
            className="custom-node-description"
          >
            {truncateAddr(data.description)}
          </a>
        
        </div>        
      </div>

      {data.telemetry && (
        <>
          <div className="custom-node-separator"></div>
          <div className="custom-node-counts">
            <div className="count-item">
              <span className="count-label">Tx:</span>
              <span ref={txCountRef} className="count-value">{data.telemetry?.txCount}</span>
            </div>
            
            <div className="count-item">
              <span className="count-label">Mon:</span>
              <span className="count-value-detector">{data.telemetry?.detectorCount}</span>
            </div>

            <div className="count-item-alerts">
              {/* <span className="count-label">Alert:</span> */}
              <span className={data.telemetry.severityCritical && data.telemetry.severityCritical != 0 ? "count-value-alert-crit" : "count-value-alert" }>{data.telemetry?.severityCritical}</span>
              <span className={data.telemetry.severityHigh && data.telemetry.severityHigh != 0 ? "count-value-alert-major" : "count-value-alert" }>{data.telemetry?.severityHigh}</span>
              <span className={data.telemetry.severityMedium && data.telemetry.severityMedium != 0 ? "count-value-alert-med" : "count-value-alert" }>{data.telemetry?.severityMedium}</span>
              <span className={data.telemetry.severityLow && data.telemetry.severityLow != 0 ? "count-value-alert-low" : "count-value-alert" }>{data.telemetry?.severityLow}</span>
            </div> 
          </div>
        </>
      )}
      
    </div>
  );
}

export default CustomNode;