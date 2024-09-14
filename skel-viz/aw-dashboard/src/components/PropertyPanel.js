import React, { useState, useEffect } from 'react';

function PropertyPanel({ hexagon, onHexagonUpdate }) {
  const [name, setName] = useState('');
  const [addr, setAddr] = useState('');
  const [radius, setRadius] = useState('');

  useEffect(() => {
    if (hexagon) {
      setName(hexagon.name || '');
      setAddr(hexagon.addr || '');
      setRadius(hexagon.radius ? hexagon.radius.toFixed(4) : '');
    }
  }, [hexagon]);

  if (!hexagon) return <div className="property-panel empty">No hexagon selected</div>;

  const handleNameChange = (e) => setName(e.target.value);
  const handleAddrChange = (e) => setAddr(e.target.value);
  const handleRadiusChange = (e) => {
    const value = e.target.value;
    setRadius(value);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    const numRadius = parseFloat(radius);
    if (!isNaN(numRadius) && numRadius > 0) {
      onHexagonUpdate({ ...hexagon, name, addr, radius: numRadius });
    } else {
      // Handle invalid radius (e.g., show an error message)
      console.error('Invalid radius value');
    }
  };

  return (
    <div className="property-panel">
      <h3>Hexagon Properties</h3>
      <form onSubmit={handleSubmit}>
        <div className="property">
          <label htmlFor="name">Name:</label>
          <input
            id="name"
            type="text"
            value={name}
            onChange={handleNameChange}
          />
        </div>
        <div className="property">
          <label htmlFor="addr">Address:</label>
          <input
            id="addr"
            type="text"
            value={addr}
            onChange={handleAddrChange}
          />
        </div>
        <div className="property">
          <label htmlFor="radius">Radius (km):</label>
          <input
            id="radius"
            type="number"
            value={radius}
            onChange={handleRadiusChange}
            step="0.0001"
            min="0"
          />
        </div>
        <button type="submit">Update</button>
      </form>
      
      <hr />
      
      <div className="all-properties">
        {Object.entries(hexagon).map(([key, value]) => (
          <React.Fragment key={key}>
            <div className="property">
              <span className="key">{key}:</span>
              <span className="value">
                {typeof value === 'number' ? value.toFixed(6) : String(value)}
              </span>
            </div>
            <hr />
          </React.Fragment>
        ))}
      </div>
    </div>
  );
}

export default PropertyPanel;
