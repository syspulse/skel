import React, { useEffect, useRef, useState } from 'react';
import './TopPanel.css'; // Ensure this CSS file is created

interface TopPanelProps {
  onLogin: () => void;
  onSearch: (searchText: string) => void; // New prop for search
}

const TopPanel: React.FC<TopPanelProps> = ({ onLogin,onSearch }) => {
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const [searchText, setSearchText] = useState('');
  const dropdownRef = useRef<HTMLDivElement>(null);

  const toggleDropdown = (event: React.MouseEvent) => {
    event.stopPropagation(); // Prevent event bubbling
    setDropdownOpen(!dropdownOpen);
  };

  const handleLogin = () => {
    onLogin();
    setDropdownOpen(false);
  };

  const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;    
    setSearchText(value);
    onSearch(value); // Call the onSearch prop to filter nodes
  };


  const handleClickOutside = (event: MouseEvent) => {
    if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
      setDropdownOpen(false);
    }
  };

  useEffect(() => {
    // Add event listener for clicks outside the dropdown
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('click', handleClickOutside); // Add click event listener
    return () => {
      // Cleanup the event listener on component unmount
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('click', handleClickOutside); // Cleanup click event listener
    };
  }, []);

  return (
    <div className="top-panel">
      <div className="logo-container">
        <img src="/assets/logo.svg" alt="" className="logo-image" /> {/* Update the path to your logo image */}
        <div className="logo">Helicopter</div>
      </div>
      <input
        type="text"
        placeholder="Search..."
        className="search-input"
        value={searchText}
        onChange={handleSearchChange} // Update search text on change
      />
      <div className="user-profile">
        <div className="user-icon" onClick={toggleDropdown}>
          U
        </div>
        {dropdownOpen && (
          <div className="dropdown-menu"  ref={dropdownRef}>
            <button className="dropdown-item" onClick={handleLogin}>Login</button>
            <hr />
            <button className="dropdown-item">Settings</button>
            <button className="dropdown-item">Help</button>
          </div>
        )}
      </div>
    </div>
  );
};

export default TopPanel;