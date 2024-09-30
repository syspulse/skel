import React, { useState, useCallback, useEffect, useRef } from 'react';
import TopPanel from './components/TopPanel';
import HexagonMap from './components/Map';
import PropertyPanel from './components/PropertyPanel';
import AircraftPropertyPanel from './components/AircraftPropertyPanel';
import * as turf from '@turf/turf';
import { Area } from './core/Area.ts';
import { Aircraft, planeMove } from './core/Aircraft.ts';
import './App.css';
import DEFAULT_LOCATIONS from './core/Location.js';

function App() {
  const [hexagons, setHexagons] = useState(null);
  const [selectedHexagon, setSelectedHexagon] = useState(null);
  const [mapCenter, setMapCenter] = useState(null);
  const [aircraft, setAircraft] = useState([]);
  const [selectedAircraftId, setSelectedAircraftId] = useState(null);
  const [mapViewState, setMapViewState] = useState(DEFAULT_LOCATIONS['San Francisco']);
  const lastUpdateRef = useRef({});

  const connectWebSocket = useCallback(() => {
    console.log('Attempting to connect to WebSocket...');
    const socket = new WebSocket('ws://localhost:9300');

    socket.onopen = (event) => {
      console.log('WebSocket connection opened:', event);      
    };

    socket.onmessage = (event) => {
      //console.log('WebSocket message received:', event.data);
      try {
        const data = JSON.parse(event.data);
        updateAircraft(data);
      } catch (error) {
        console.error('Error parsing WebSocket message:', error);
      }
    };

    socket.onclose = (event) => {
      console.log('WebSocket connection closed. Details:', {
        code: event.code,
        reason: event.reason,
        wasClean: event.wasClean
      });
      
      setTimeout(connectWebSocket, 1000);
    };

    socket.onerror = (error) => {
      console.error('WebSocket error:', error);      
    };

    return socket;
  }, []);

  const updateAircraft = useCallback((data) => {
    console.log("updateAircraft ============>",data.addr);
    
    const updateKey = `${data.addr.icaoId}-${data.loc.lat}-${data.loc.lon}-${data.loc.alt.alt}`;
    if (lastUpdateRef.current[updateKey]) {
      console.log(`>>> Skipping update: ${data.addr.icaoId}`);
      return;
    }
    lastUpdateRef.current[updateKey] = true;

    const updatedAircraft = new Aircraft(
      data.addr.icaoId, // Use icaoId as the unique identifier
      data.addr.icaoCallsign,
      data.addr.icaoId,
      data.loc.vel, // SHOULD BE ALTITUDE !
      data.loc.lon,
      data.loc.lat,
    );

    setAircraft(prevAircraft => {
      const existingIndex = prevAircraft.findIndex(a => a.icaoId === data.addr.icaoId);
      if (existingIndex !== -1) {
        // Update existing aircraft
        return prevAircraft.map((aircraft, index) => 
          index === existingIndex ? { ...aircraft, ...updatedAircraft } : aircraft
        );
      } else {
        // Add new aircraft
        return [...prevAircraft, updatedAircraft];
      }
    });

    console.log("Aircraft state updated:", updatedAircraft);
  }, []); // Empty dependency array

  useEffect(() => {
    const socket = connectWebSocket();

    return () => {
      console.log('Cleaning up WebSocket connection...');
      socket.close();
    };
  }, [connectWebSocket]);

  const createInitialHexagons = useCallback(() => {
    const bbox = [-122.5, 37.7, -122.3, 37.9]; // Bounding box for San Francisco
    // Expanded bounding box for a larger area (roughly covering California)
    //const bbox = [-124.5, 32.5, -114.0, 42.0];
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

  const createInitialAircraft = useCallback(() => {
    const center = [-122.4194, 37.7749]; // San Francisco coordinates
    const radius = 0.5; // 50 km radius
    return [
      // new Aircraft(1, 'SFO001', 'A1B2C3', 0.1, center[0], center[1] + radius),
      // new Aircraft(2, 'SFO002', 'D4E5F6', 0.5, center[0] + radius, center[1]),
      // new Aircraft(3, 'SFO003', 'G7H8I9', 3.0, center[0], center[1] - radius),
      new Aircraft(1, 'SFO001', 'A1B2C3', 0.5, center[0], center[1]),
      new Aircraft(2, 'SFO002', 'D4E5F6', 1.0, center[0] , center[1]),
      new Aircraft(3, 'SFO003', 'G7H8I9', 3.0, center[0], center[1]),
    ];
  }, []);

  useEffect(() => {
    if (aircraft.length === 0) {
      setAircraft(createInitialAircraft());
    }
  }, [aircraft, createInitialAircraft]);

  // useEffect(() => {
  //   const intervalId = setInterval(() => {
  //     setAircraft(prevAircraft => {
  //       return prevAircraft.filter(plane => plane.callsign.startsWith('SF')).map(plane => planeMove(plane));
  //     });
  //   }, 1000);

  //   return () => clearInterval(intervalId);
  // }, []);

  const handleHexagonSelect = useCallback((hexagon) => {
    setSelectedHexagon(hexagon);
  }, []);

  const handleAircraftSelect = useCallback((aircraft) => {
    setSelectedAircraftId(aircraft ? aircraft.id : null);
    
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

  const handleAircraftUpdate = useCallback((updatedAircraft) => {
    setAircraft(prevAircraft => 
      prevAircraft.map(a => a.id === updatedAircraft.id ? updatedAircraft : a)
    );
  }, []);

  const handleSearch = useCallback((searchTerm) => {
    if (!hexagons) return;

    const lowercaseSearchTerm = searchTerm.toLowerCase();
    const matchingHexagon = hexagons.features.find(feature => 
      feature.properties.name.toLowerCase().startsWith(lowercaseSearchTerm)
    );

    if (matchingHexagon) {
      setSelectedHexagon(matchingHexagon.properties);
      
      // Calculate the center of the hexagon
      const center = turf.center(matchingHexagon);
      setMapCenter(center.geometry.coordinates);
    }
  }, [hexagons]);

  // Derive selectedAircraft from aircraft and selectedAircraftId
  const selectedAircraft = aircraft.find(a => a.id === selectedAircraftId);

  const handleLocationChange = (newLocation) => {
    setMapViewState(newLocation);
  };

  return (
    <div className="app">
      <TopPanel 
        onSearch={handleSearch} 
        onLocationChange={handleLocationChange}
      />
      <div className="main-content">
        <HexagonMap
          onHexagonSelect={handleHexagonSelect}
          hexagons={hexagons}
          setHexagons={setHexagons}
          selectedHexagon={selectedHexagon}
          mapCenter={mapCenter}
          aircraft={aircraft}
          selectedAircraft={selectedAircraft}
          onAircraftSelect={handleAircraftSelect}          
          viewState={mapViewState}          
          setViewState={setMapViewState}
        />
        {selectedHexagon && (
          <PropertyPanel
            hexagon={selectedHexagon}
            onHexagonUpdate={handleHexagonUpdate}
          />
        )}
        {selectedAircraft && (
          <AircraftPropertyPanel
            aircraft={selectedAircraft}
            onAircraftUpdate={handleAircraftUpdate}
          />
        )}
      </div>
    </div>
  );
}

export default App;
