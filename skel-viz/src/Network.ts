export interface Network {
  id: string;
  name: string;
  icon: string;
}

export const networksArray: Network[] = [
  { id: 'ethereum', name: 'Ethereum', icon: '/assets/ethereum-icon.png' },
  { id: 'arbitrum', name: 'Arbitrum', icon: '/assets/arbitrum-icon.png' },
  { id: 'optimism', name: 'Optimism', icon: '/assets/optimism-icon.png' },
  { id: 'polygon', name: 'Polygon', icon: '/assets/polygon-icon.png' },
];

export const networksMap: Map<string, Network> = new Map(networksArray.map(network => [network.id, network]));
