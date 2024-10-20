export interface Network {
  id: string;
  name: string;
  icon: string;
  explorer: string;
}

export const networks: Network[] = [
  { id: 'ethereum', name: 'Ethereum', icon: '/assets/ethereum-icon.png', explorer: 'https://etherscan.io' },
  { id: 'arbitrum', name: 'Arbitrum', icon: '/assets/arbitrum-icon.png', explorer: 'https://arbiscan.io' },
  { id: 'optimism', name: 'Optimism', icon: '/assets/optimism-icon.png', explorer: 'https://optimistic.etherscan.io' },
  { id: 'polygon', name: 'Polygon', icon: '/assets/polygon-icon.png', explorer: 'https://polygonscan.com' },
  
  { id: 'bsc', name: 'BSC', icon: '/assets/bsc-icon.png', explorer: 'https://bscscan.com' },
  { id: 'avalanche', name: 'Avalanche', icon: '/assets/avalanche-icon.png', explorer: 'https://cchain.explorer.avax.network' },
  { id: 'fantom', name: 'Fantom', icon: '/assets/fantom-icon.png', explorer: 'https://ftmscan.com' },
  { id: 'gnosis', name: 'Gnosis', icon: '/assets/gnosis-icon.png', explorer: 'https://gnosisscan.io' },
  { id: 'base', name: 'Base', icon: '/assets/base-icon.png', explorer: 'https://explorer.base.org' },
  { id: 'blast', name: 'Blast', icon: '/assets/blast-icon.png', explorer: 'https://explorer.blastapi.io' },

  { id: 'zksync', name: 'ZKsync', icon: '/assets/zksync-icon.png', explorer: 'https://explorer.zksync.io' },
  { id: 'scroll', name: 'Scroll', icon: '/assets/scroll-icon.png', explorer: 'https://explorer.scroll.io' },

  { id: 'sepolia', name: 'Sepolia', icon: '/assets/sepolia-icon.png', explorer: 'https://sepolia.etherscan.io' },
  { id: 'anvil', name: 'Anvil', icon: '/assets/anvil-icon.png', explorer: 'https://anvil.hacken.cloud' },

  { id: 'vechain', name: 'VeChain', icon: '/assets/vechain-icon.png', explorer: 'https://explore.vechain.org' },

];

export const networksMap: Map<string, Network> = new Map(networks.map(network => [network.id, network]));
