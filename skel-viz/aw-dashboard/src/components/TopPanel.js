import React, { useState } from 'react';

function TopPanel({ onSearch }) {
  const [searchTerm, setSearchTerm] = useState('');

  const handleSearchChange = (e) => {
    const value = e.target.value;
    setSearchTerm(value);
    onSearch(value);
  };

  return (
    <div className="top-panel">
      <div className="logo">Aeroware</div>
      <div className="search-container">
        <input
          type="text"
          placeholder="Search hexagon by name..."
          value={searchTerm}
          onChange={handleSearchChange}
        />
      </div>
      <div className="user-info">User</div>
    </div>
  );
}

export default TopPanel;
