import React, { memo, useCallback } from 'react';
import { Handle, Position, NodeProps, useReactFlow, Node } from 'reactflow';

interface CustomNodeData {
  title: string;
  description: string;
  icon: string;
  selected?: boolean;
  extraHandles?: { type: 'source' | 'target',  position: Position}[];
}

function CustomNode({ data, id, selected }: NodeProps<CustomNodeData>) {
  const { deleteElements, setNodes } = useReactFlow();

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

  const truncateAddr = (addr: string) => {
    if (addr.length <= 16) return addr;
    return `${addr.slice(0, 10)}...${addr.slice(-10)}`;
  };

  return (
    <div 
      style={{ 
        padding: '10px', 
        border: '1px solid #ddd', 
        borderRadius: '5px', 
        background: selected ? '#ddd' : 'white',
        position: 'relative'
      }}
      //onDoubleClick={onDoubleClick}
    >
      
      <button className="delete-button" onClick={onDelete}>×</button>
      
      {/* <Handle type="target" position={Position.Top} id="top-target" />
      <Handle type="source" position={Position.Top} id="top-source" />
      <Handle type="target" position={Position.Left} id="left-target" />
      <Handle type="source" position={Position.Left} id="left-source" />
      <Handle type="target" position={Position.Right} id="right-target" />
      <Handle type="source" position={Position.Right} id="right-source" />
      <Handle type="target" position={Position.Bottom} id="bottom-target" />
      <Handle type="source" position={Position.Bottom} id="bottom-source" /> */}
     
      <Handle type="target" position={Position.Top} id="top-target" className="target-handle" />
      <Handle type="source" position={Position.Top} id="top-source" className="source-handle" />
      
      <Handle type="target" position={Position.Left} id="left-target" className="target-handle" />
      <Handle type="source" position={Position.Left} id="left-source" className="source-handle"  />
      
      <Handle type="target" position={Position.Right} id="right-target" className="target-handle" />
      <Handle type="source" position={Position.Right} id="right-source" className="source-handle" />
      
      <Handle type="target" position={Position.Bottom} id="bottom-target" className="target-handle" />
      <Handle type="source" position={Position.Bottom} id="bottom-source" className="source-handle" />

      {/* <Handle type="source" position={Position.Top} id="top-source-10" className="source-handle" style={{transform:'translate(+50%, 0%)'}} />  */}
      {data.extraHandles?.map((handle, index) => (
        // <Handle
        //   key={`${handle.type}-${index}`}
        //   type={handle.type}
        //   position={handle.position}
        //   id={`${handle.type}-${index}`}
        //   className={`${handle.type}-handle`}
          
        //   style={{
        //     // left: `${handle.x}%`,
        //     // top: `${handle.y}%`,
        //     transform: 'translate(+50%, 0%)',
        //     backgroundColor: "red",
        //     position: 'absolute'
        //   }}
        // />        
        <Handle type="source" position={Position.Top} id="top-source-10" className="source-handle" style={{transform:'translate(+50%, 0%)'}} />
      ))}
      
      <div style={{ display: 'flex', alignItems: 'center' }}>
        {/* <span style={{ marginRight: '10px', fontSize: '24px' }}>{data.icon}</span> */}
        {/* <div className="custom-node-icon">{data.icon}</div> */}

        <img 
          src={data.icon} 
          alt={data.title} 
          className="custom-node-icon"          
        />

        <div>
                    
          {/* <p style={{ margin: '5px 0 0' }}>{data.description}</p> */}
          <div className="custom-node-title">{data.title}</div>
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
      
    </div>
  );
}

export default CustomNode;
