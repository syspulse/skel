import React, { useState, useEffect } from 'react';

function PropertyPanel({ hexagon, onHexagonUpdate }) {
  const [name, setName] = useState('');
  const [addr, setAddr] = useState('');
  const [radius, setRadius] = useState('');

  useEffect(() => {
    if (hexagon) {
      setName(hexagon.name);
      setAddr(hexagon.addr);
      setRadius(hexagon.radius.toFixed(2));
    }
  }, [hexagon]);

  if (!hexagon) return <div className="property-panel empty">No hexagon selected</div>;

  const handleNameChange = (e) => setName(e.target.value);
  const handleAddrChange = (e) => setAddr(e.target.value);
  const handleRadiusChange = (e) => {
    const value = parseFloat(e.target.value);
    if (!isNaN(value) && value > 0) {
      setRadius(value.toFixed(2));
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onHexagonUpdate({ ...hexagon, name, addr, radius: parseFloat(radius) });
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
            step="0.1"
            min="0.1"
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
                {typeof value === 'number' ? value.toFixed(6) : value.toString()}
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
