import React from 'react';

const icons = [
  'https://cryptologos.cc/logos/uniswap-uni-logo.png',
  'https://cryptologos.cc/logos/ethereum-eth-logo.png',
  'https://cryptologos.cc/logos/bitcoin-btc-logo.png',
  '/assets/contract.png',
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
