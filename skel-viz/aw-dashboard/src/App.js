import React, { useState, useCallback } from 'react';
import TopPanel from './components/TopPanel';
import PropertyPanel from './components/PropertyPanel';
import HexagonMap from './components/Map';
import './App.css';

function App() {
  const [hexagons, setHexagons] = useState(null);
  const [selectedHexagon, setSelectedHexagon] = useState(null);

  const handleHexagonSelect = useCallback((hexagon) => {
    setSelectedHexagon(hexagon);
  }, []);

  const handleHexagonUpdate = useCallback((updatedHexagon) => {
    setHexagons((prevHexagons) => {
      if (!prevHexagons) return null;
      const updatedFeatures = prevHexagons.features.map((feature) => {
        if (feature.properties.id === updatedHexagon.id) {
          return { ...feature, properties: updatedHexagon };
        }
        return feature;
      });
      return { ...prevHexagons, features: updatedFeatures };
    });
    setSelectedHexagon(updatedHexagon);
  }, []);

  return (
    <div className="app">
      <TopPanel />
      <div className="main-content">
        <HexagonMap
          onHexagonSelect={handleHexagonSelect}
          hexagons={hexagons}
          setHexagons={setHexagons}
        />
        <PropertyPanel
          hexagon={selectedHexagon}
          onHexagonUpdate={handleHexagonUpdate}
        />
      </div>
    </div>
  );
}

export default App;
