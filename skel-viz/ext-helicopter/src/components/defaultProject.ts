import { Edge, MarkerType, Node } from 'reactflow';

const initialNodes: Node[] = [
  {
    id: 'gpt-1',
    type: 'custom',
    position: { x: -200, y: 0 },
    data: { 
      nodeType: 'gpt',
      title: 'GPT Node',
      description: '',
      network: '',
      icon: '',
      tags: '',
      inputText: `# Example Content

## Table
| Name | Type | Value |
|------|------|-------|
| ETH | Token | 2000 |
| BTC | Token | 40000 |
| USDC | Stable | 1 |

## Standard Image
![Uniswap Logo](https://assets.coingecko.com/coins/images/12504/standard/uniswap-logo.png)

## Resized Image
<img src="https://assets.coingecko.com/coins/images/12504/standard/uniswap-logo.png" width="32" alt="Uniswap Logo">`
    },
  },
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
      icon: 'https://assets.coingecko.com/coins/images/12504/standard/uniswap-logo.png',
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
      icon: 'https://assets.coingecko.com/coins/images/12504/standard/uniswap-logo.png',
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
  {
    id: 'pie-chart-1',
    type: 'custom',
    position: { x: 0, y: -150 },
    data: {
      nodeType: 'chart',
      title: 'Balance Distribution',
      description: '',
      icon: '',
      tags: '',
      network: '',
      chartData: {
        style: 'pie',
        data: [
          { address: '0x6e850415f94c47f6e9dc5d89c906f7387697f771', current_balance: 14251464.546782 },
          { address: '0x3fb4fbc8c83acd1b1d5f9d74eeeeef83940c3f1c', current_balance: 8273572 },
          { address: '0xb0a3a2b60e969afd26561429aa4c1444c57e4411', current_balance: 3222259.3200000003 },
          { address: '0x7c8aa3dd42fc0c9646552c638af532eb56ccbea8', current_balance: 2029652.7790632173 },
          { address: '0x89829cb5f2958282ce98d5b7fa7b8da5f22ec7ac', current_balance: 1919002.7702221212 },
          { address: '0xc5c9da1c2c64ed6eb60d0230c1a3b7e5cace1628', current_balance: 1779618.314404438 },
          { address: '0xc59a0d903ae08ae9e2f777ef4716eea414c2ccfb', current_balance: 1779618.314404438 },
          { address: '0x47bf9ceb0515f3213130a34afca6a2d5424d0fa2', current_balance: 1711581.3426184566 },
          { address: '0x376c7fc57f008e96e338fedcedd8660a7e10e893', current_balance: 1562109.006556454 },
          { address: '0xf04627785a0a6a9287b03fd5be4671fca9dc1049', current_balance: 1562109.006556454 }
        ]
      }
    }
  },
  {
    id: 'timeseries-1',
    type: 'custom',
    position: { x: 250, y: -150 },
    data: {
      nodeType: 'chart',
      title: 'Transaction History',
      description: '',
      icon: '',
      tags: '',
      network: '',
      chartData: {
        style: 'timeseries',
        data: [
          { block_date: '2024-08-30', total_transactions: 26189 },
          { block_date: '2024-08-31', total_transactions: 85719 },
          { block_date: '2024-09-01', total_transactions: 85717 },
          { block_date: '2024-09-02', total_transactions: 85712 },
          { block_date: '2024-09-03', total_transactions: 85714 },
          { block_date: '2024-09-04', total_transactions: 85708 },
          { block_date: '2024-09-05', total_transactions: 85715 },
          { block_date: '2024-09-06', total_transactions: 85340 },
          { block_date: '2024-09-07', total_transactions: 85707 },
          { block_date: '2024-09-08', total_transactions: 85712 }
        ]
      }
    }
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
