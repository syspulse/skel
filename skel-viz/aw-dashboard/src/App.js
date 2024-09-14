import React, { useState, useCallback, useEffect } from 'react';
import TopPanel from './components/TopPanel';
import HexagonMap from './components/Map';
import PropertyPanel from './components/PropertyPanel';
import * as turf from '@turf/turf';
import { Area } from './core/Area.ts';
import './App.css';

function App() {
  const [hexagons, setHexagons] = useState(null);
  const [selectedHexagon, setSelectedHexagon] = useState(null);

  const createInitialHexagons = useCallback(() => {
    const bbox = [-122.5, 37.7, -122.3, 37.9]; // Bounding box for San Francisco
    const hexagonFeatures = [];

    for (let i = 0; i < 10; i++) {
      const center = turf.randomPosition(bbox);
      const radius = Math.random() * 1.5 + 0.5; // Random between 0.5 and 2.0
      const hexagon = turf.circle(center, radius, { steps: 6, units: 'kilometers' });
      const area = new Area(i, center, radius);
      hexagon.properties = area;
      hexagonFeatures.push(hexagon);
    }

    return turf.featureCollection(hexagonFeatures);
  }, []);

  useEffect(() => {
    if (!hexagons) {
      setHexagons(createInitialHexagons());
    }
  }, [hexagons, createInitialHexagons]);

  const handleHexagonSelect = useCallback((hexagon) => {
    setSelectedHexagon(hexagon);
  }, []);

  const handleHexagonUpdate = useCallback((updatedHexagon) => {
    setHexagons((prevHexagons) => {
      if (!prevHexagons) return null;
      
      const updatedFeatures = prevHexagons.features.map((feature) => {
        if (feature.properties.id === updatedHexagon.id) {
          // Ensure all properties are included
          const updatedProperties = {
            ...feature.properties,
            ...updatedHexagon
          };
          
          // Recreate the hexagon geometry with the new radius and center
          const center = [updatedProperties.longitude, updatedProperties.latitude];
          const newGeometry = turf.circle(center, updatedProperties.radius, { steps: 6, units: 'kilometers' }).geometry;
          
          return {
            ...feature,
            geometry: newGeometry,
            properties: updatedProperties
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
          selectedHexagon={selectedHexagon}
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
