import React, { useState, useEffect } from 'react';

function PropertyPanel({ hexagon, onHexagonUpdate }) {
  const [name, setName] = useState('');
  const [addr, setAddr] = useState('');
  const [radius, setRadius] = useState('');
  const [longitude, setLongitude] = useState('');
  const [latitude, setLatitude] = useState('');

  useEffect(() => {
    if (hexagon) {
      setName(hexagon.name || '');
      setAddr(hexagon.addr || '');
      setRadius(hexagon.radius ? hexagon.radius.toFixed(4) : '');
      setLongitude(hexagon.longitude ? hexagon.longitude.toFixed(6) : '');
      setLatitude(hexagon.latitude ? hexagon.latitude.toFixed(6) : '');
    }
  }, [hexagon]);

  if (!hexagon) return <div className="property-panel empty">No hexagon selected</div>;

  const handleNameChange = (e) => setName(e.target.value);
  const handleAddrChange = (e) => setAddr(e.target.value);
  const handleRadiusChange = (e) => setRadius(e.target.value);
  const handleLongitudeChange = (e) => setLongitude(e.target.value);
  const handleLatitudeChange = (e) => setLatitude(e.target.value);

  const handleSubmit = (e) => {
    e.preventDefault();
    const numRadius = parseFloat(radius);
    const numLongitude = parseFloat(longitude);
    const numLatitude = parseFloat(latitude);
    if (!isNaN(numRadius) && numRadius > 0 &&
        !isNaN(numLongitude) && !isNaN(numLatitude)) {
      onHexagonUpdate({
        ...hexagon,
        name,
        addr,
        radius: numRadius,
        longitude: numLongitude,
        latitude: numLatitude
      });
    } else {
      console.error('Invalid input values');
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
        <div className="property">
          <label htmlFor="latitude">Latitude:</label>
          <input
            id="latitude"
            type="number"
            value={latitude}
            onChange={handleLatitudeChange}
            step="0.000001"
          />
        </div>
        <div className="property">
          <label htmlFor="longitude">Longitude:</label>
          <input
            id="longitude"
            type="number"
            value={longitude}
            onChange={handleLongitudeChange}
            step="0.000001"
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
