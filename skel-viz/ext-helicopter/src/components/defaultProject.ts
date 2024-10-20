import { Edge, MarkerType, Node } from 'reactflow';

const initialNodes: Node[] = [
  {
    id: '2134',
    type: 'custom',
    position: { x: 0, y: 0 },
    data: { 
      title: 'Deployer', 
      description: '0x946E9C780F3c79D80e51e68d259d0D7E794F2124', 
      network: 'ethereum',
      icon: '/assets/key.png',
      tags: 'Uniswap',
      telemetry: {
        txCount: 0, 
        alertCount: 0,
        detectorCount: 0,
      },
    },
  },
  {
    id: '2135',
    type: 'custom',
    position: { x: 300, y: 0 },
    data: { 
      title: 'Universal RouterV2', 
      description: '0x3fC91A3afd70395Cd496C647d5a6CC9D4B2b7FAD', 
      network: 'ethereum',
      icon: 'https://cryptologos.cc/logos/uniswap-uni-logo.png',
      tags: 'DEX,Uniswap',
      telemetry: {
        txCount: 0, 
        alertCount: 0, 
        detectorCount: 0,
      },
    },
  },
  {
    id: '2136',
    type: 'custom',
    position: { x: 50, y: 200 },
    data: { 
      title: 'UNI', 
      description: '0x1f9840a85d5aF5bf1D1762F925BDADdC4201F984', 
      network: 'ethereum',
      icon: 'https://cryptologos.cc/logos/uniswap-uni-logo.png',
      tags: 'ERC20',
      // This node doesn't have telemetry, so counters won't be displayed      
    },
  },
  {
    id: '956',
    type: 'custom',
    position: { x: 300, y: 200 },
    data: { 
      title: 'RouterV3', 
      description: '0x7a250d5630b4cf539739df2c5dacb4c659f2488d', 
      network: 'ethereum',
      icon: '',
      tags: 'DEX,Uniswap',
      telemetry: {
        txCount: 0, 
        alertCount: 0,
        detectorCount: 0,
      },
    },
  },
];


const initialEdges: Edge[] = [
  { 
    id: 'e0-1', 
    source: '2134', 
    target: '2135',
    sourceHandle: 'right-source',
    targetHandle: 'left-target',
    markerEnd: { type: MarkerType.ArrowClosed },
    data: { transaction: '0x' },
    label: '0x',
    animated: true
  },
  { 
    id: 'e0-2', 
    source: '2134', 
    target: '2136',
    sourceHandle: 'bottom-source',
    targetHandle: 'top-target',
    markerEnd: { type: MarkerType.ArrowClosed },
    data: { transaction: '0x' },
    label: '0x',
  }
];

export const DEFAULT_TENANT = "490-Demo";
export const DEFAULT_PROJECT = "645-Uniswap";

export { initialNodes, initialEdges };
