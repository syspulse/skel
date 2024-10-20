import React, { useState } from 'react';
import DEFAULT_LOCATIONS from '../core/Location';

function TopPanel({ onSearch, onLocationChange }) {
  const [searchTerm, setSearchTerm] = useState('');

  const handleSearchChange = (e) => {
    const value = e.target.value;
    setSearchTerm(value);
    onSearch(value);
  };

  const handleLocationChange = (e) => {
    const selectedLocation = DEFAULT_LOCATIONS[e.target.value];
    if (selectedLocation) {
      onLocationChange(selectedLocation);
    }
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
      <div className="location-selector">
        <select onChange={handleLocationChange}>
          {Object.keys(DEFAULT_LOCATIONS).map((location) => (
            <option key={location} value={location}>
              {location}
            </option>
          ))}
        </select>
      </div>
      <div className="user-info">User</div>
    </div>
  );
}

export default TopPanel;
