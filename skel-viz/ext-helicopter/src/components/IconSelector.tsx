import React from 'react';

const icons = [
  'https://assets.coingecko.com/coins/images/12504/standard/uniswap-logo.png',
  '/assets/ethereum-icon.png',
  'https://assets.coingecko.com/coins/images/1/standard/bitcoin.png',
  
  '/assets/alert-1.png',
  '/assets/alert-2.png',
  '/assets/contract.png',
  '/assets/box.svg',
  '/assets/cog-1.png',
  '/assets/cog-2.png',
  '/assets/contract.png',
  '/assets/contract-2.svg',

  '/assets/eth.svg',
  '/assets/key.png',
  '/assets/key-2.png',
  '/assets/mm.png',

  '/assets/shield.svg',
  '/assets/stake.png',
  '/assets/user-1.png',
  '/assets/user-2.png',
  '/assets/user-3.png',
  '/assets/user-4.png',

  '/assets/vault.png',
  '/assets/gnosis-safe.png',
  '/assets/wallet.png',
  '/assets/bridge.png',

  '/assets/ethereum-icon.png',
  '/assets/arbitrum-icon.png',
  '/assets/optimism-icon.png',
  '/assets/polygon-icon.png',
  '/assets/bsc-icon.png',
  '/assets/avalanche-icon.png',
  '/assets/solana-icon.png',  
  '/assets/scroll-icon.png',
  '/assets/zksync-icon.png',
  '/assets/base-icon.png',
  '/assets/blast-icon.png',
  '/assets/vechain-icon.png',
  '/assets/anvil-icon.png',
  '/assets/telos-icon.png',
  '/assets/gnosis-icon.png',
  
  // Add more icon URLs as needed
];

interface IconSelectorProps {
  selectedIcon: string;
  onSelectIcon: (icon: string) => void;
}

const IconSelector: React.FC<IconSelectorProps> = ({ selectedIcon, onSelectIcon }) => {
    return (
      <div className="icon-selector-container">
        <div className="icon-selector">
          {icons.map((icon, index) => (
            <img
              key={index}
              src={icon}
              alt={`Icon ${index + 1}`}
              className={`icon-option ${selectedIcon === icon ? 'selected' : ''}`}
              onClick={() => onSelectIcon(icon)}
            />
          ))}
        </div>
      </div>
    );
  };

export default IconSelector;
