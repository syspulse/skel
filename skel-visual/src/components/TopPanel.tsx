import React, { useEffect, useRef, useState } from 'react';
import './TopPanel.css'; // Ensure this CSS file is created
import TopMenu from './TopMenu';

interface TopPanelProps {
  onLogin: () => void;
  onSearch: (searchText: string) => void;    
  onProjectId: (projectId: string) => void;
  onRefreshFreq: (refreshFreq: number) => void;  
  searchInputRef: React.RefObject<HTMLInputElement>;
}

const TopPanel: React.FC<TopPanelProps> = ({ onLogin,onSearch,onProjectId,onRefreshFreq,searchInputRef}) => {
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [projectId, setProjectId] = useState('645');
  const [refreshFreq, setRefreshFreq] = useState(10000);

  const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;    
    setSearchText(value);
    onSearch(value); // Call the onSearch prop to filter nodes
  };

  const handleProjectIdChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setProjectId(event.target.value);
    onProjectId(event.target.value);
  };

  const handleRefreshFreqChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const freq = Number(event.target.value);
    setRefreshFreq(freq);
    onRefreshFreq(freq);
  };

  return (
    <div className="top-panel">
      <div className="logo-container">
        <img src="/assets/logo.svg" alt="" className="logo-image" /> {/* Update the path to your logo image */}
        <div className="logo">Helicopter</div>
      </div>
      <input
        ref = {searchInputRef}
        type="text"
        placeholder={`Press \u002F ...`}
        className="search-input"
        value={searchText}
        onChange={handleSearchChange} // Update search text on change
      />
      <div className="options-container">
        <label htmlFor="projectId">Project ID:</label>
        <input
          type="text"
          id="projectId"
          value={projectId}
          onChange={handleProjectIdChange}
          className="option-input-number"
        />
        <label htmlFor="refreshFreq">Refresh:</label>
        <input
          type="text"
          id="refreshFreq"
          value={refreshFreq}
          onChange={handleRefreshFreqChange}
          className="option-input-number"
        />
      </div>
      <TopMenu onLogin={onLogin} />
    </div>
  );
};

export default TopPanel;