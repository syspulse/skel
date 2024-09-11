import React, { useState, useEffect } from 'react';
import './NetworkDropdown.css';

interface Network {
  id: string;
  name: string;
  icon: string;
}

const networksArray: Network[] = [
  { id: 'ethereum', name: 'Ethereum', icon: '/assets/ethereum-icon.png' },
  { id: 'arbitrum', name: 'Arbitrum', icon: '/assets/arbitrum-icon.png' },
  { id: 'optimism', name: 'Optimism', icon: '/assets/optimism-icon.png' },
  { id: 'polygon', name: 'Polygon', icon: '/assets/polygon-icon.png' },
];

const networksMap: Map<string, Network> = new Map(networksArray.map(network => [network.id, network]));

interface NetworkDropdownProps {
  selectedNetwork: string;
  onNetworkChange: (networkId: string) => void;
}

const NetworkDropdown: React.FC<NetworkDropdownProps> = ({ selectedNetwork, onNetworkChange }) => {
  const [isOpen, setIsOpen] = useState(false);
  const [currentNetwork, setCurrentNetwork] = useState<Network | undefined>(networksMap.get(selectedNetwork));

  useEffect(() => {
    setCurrentNetwork(networksMap.get(selectedNetwork));
  }, [selectedNetwork]);

  const handleNetworkSelect = (network: Network) => {
    setIsOpen(false);
    setCurrentNetwork(network);
    onNetworkChange(network.id);
  };

  return (
    <div className="network-dropdown">
      <div className="selected-network" onClick={() => setIsOpen(!isOpen)}>
        {currentNetwork && (
          <>
            <img src={currentNetwork.icon} alt={currentNetwork.name} className="network-icon" />
            <span>{currentNetwork.name}</span>
          </>
        )}
        <span className="dropdown-arrow">▼</span>
      </div>
      {isOpen && (
        <ul className="network-list">
          {Array.from(networksMap.values()).map((network) => (
            <li key={network.id} onClick={() => handleNetworkSelect(network)}>
              <img src={network.icon} alt={network.name} className="network-icon" />
              <span>{network.name}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default NetworkDropdown;