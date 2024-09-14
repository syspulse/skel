import React from 'react';

function PropertyPanel({ hexagon }) {
  if (!hexagon) 
    return (
    <div className="property-panel empty"></div>
  );

  return (
    <div className="property-panel">
      {Object.entries(hexagon).map(([key, value]) => (
        <div key={key} className="property">
          <span className="key">{key}:</span>
          <span className="value">{value}</span>
        </div>
      ))}
    </div>
  );
}

export default PropertyPanel;
