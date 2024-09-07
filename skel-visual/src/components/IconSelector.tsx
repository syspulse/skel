import React from 'react';

const icons = [
  'https://cryptologos.cc/logos/uniswap-uni-logo.png',
  'https://cryptologos.cc/logos/ethereum-eth-logo.png',
  'https://cryptologos.cc/logos/bitcoin-btc-logo.png',
  
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
