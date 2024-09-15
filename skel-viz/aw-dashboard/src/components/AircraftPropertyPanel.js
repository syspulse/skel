import React from 'react';

function AircraftPropertyPanel({ aircraft, onAircraftUpdate }) {
  if (!aircraft) return null;

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    onAircraftUpdate({ ...aircraft, [name]: value });
  };

  return (
    <div className="property-panel">
      <h3>Aircraft Properties</h3>
      <div className="property">
        <label htmlFor="callsign">Callsign:</label>
        <input
          id="callsign"
          type="text"
          name="callsign"
          value={aircraft.callsign}
          onChange={handleInputChange}
        />
      </div>
      <div className="property">
        <label htmlFor="icao">ICAO:</label>
        <input
          id="icao"
          type="text"
          name="icao"
          value={aircraft.icao}
          onChange={handleInputChange}
        />
      </div>
      <div className="property">
        <label>Velocity:</label>
        <input
          className="read-only"
          type="number"
          name="velocity"
          value={aircraft.velocity}
          onChange={handleInputChange}
          readOnly
        />
      </div>
      <div className="property">
        <label>Latitude:</label>
        <input
          className="read-only"
          type="number"
          name="latitude"
          value={aircraft.latitude}
          onChange={handleInputChange}
        />
      </div>
      <div className="property">
        <label>Longitude:</label>
        <input
          className="read-only"
          type="number"
          name="longitude"
          value={aircraft.longitude}
          onChange={handleInputChange}
          readOnly
        />
      </div>
    </div>
  );
}

export default AircraftPropertyPanel;