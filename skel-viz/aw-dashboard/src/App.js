import React, { useState, useCallback } from 'react';
import TopPanel from './components/TopPanel';
import HexagonMap from './components/Map';
import PropertyPanel from './components/PropertyPanel';
import * as turf from '@turf/turf';
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
          // Recreate the hexagon geometry with the new radius
          const center = [updatedHexagon.longitude, updatedHexagon.latitude];
          const newGeometry = turf.circle(center, updatedHexagon.radius, { steps: 6, units: 'kilometers' }).geometry;
          
          return {
            ...feature,
            geometry: newGeometry,
            properties: updatedHexagon
          };
        }
        return feature;
      });

      return turf.featureCollection(updatedFeatures);
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
