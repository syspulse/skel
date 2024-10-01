import React, { useState, useEffect, useCallback, useRef } from 'react';
import { FixedSizeList as List } from 'react-window';
import './AircraftTracker.css';

const aircraftUpdatId = 10;
const aircraftMax = 7;
const updateDeleteMax = 3;

const AircraftTracker = () => {
  const [aircrafts, setAircrafts] = useState(new Map([
    ['AC1', { id: 'AC1', name: 'Aircraft 1', lon: 0, lat: 0, updateCount: 0 }],
    ['AC2', { id: 'AC2', name: 'Aircraft 2', lon: 0, lat: 0, updateCount: 0 }],
    ['AC3', { id: 'AC3', name: 'Aircraft 3', lon: 0, lat: 0, updateCount: 0 }],
  ]));
  const [socket, setSocket] = useState(null);
  const reconnectTimeoutRef = useRef(null);

  // Function to update aircraft coordinates
  const updateAircraftCoordinates = useCallback((aircraftId) => {
    setAircrafts((prevAircrafts) => {
      const newAircrafts = new Map(prevAircrafts);
      const aircraft = newAircrafts.get(aircraftId);
      if (aircraft) {
        const newUpdateCount = aircraft.updateCount + 1;
        newAircrafts.set(aircraftId, {
          ...aircraft,
          lon: Math.random() * 180 - 90,
          lat: Math.random() * 180 - 90,
          updateCount: newUpdateCount
        });

        if (newUpdateCount >= updateDeleteMax && parseInt(aircraftId) >= aircraftUpdatId) {
          newAircrafts.delete(aircraftId);
        }
      }
      return newAircrafts;
    });
  }, []);

  // Function to handle aircraft updates based on WebSocket messages
  const handleAircraftUpdate = useCallback((message) => {
    const messageNumber = parseInt(message);
    const id = aircraftUpdatId + (messageNumber) % aircraftMax;

    setAircrafts(prevAircrafts => {
      
      if (!isNaN(messageNumber) && id !== 0 && !prevAircrafts.has(id)) { 
        
        const newAircrafts = new Map(prevAircrafts);
        newAircrafts.set(id, { id: id, name: `Aircraft ${message}`, lon: 0, lat: 0, updateCount: 0 });
        
        // Schedule an update for the new aircraft
        //setTimeout(() => updateAircraftCoordinates(id), 0);
        
        return newAircrafts;
      } else {
        // Update a random existing aircraft
        const aircraftIds = Array.from(prevAircrafts.keys());
        if (aircraftIds.length > 0) {
          const randomId = aircraftIds[Math.floor(Math.random() * aircraftIds.length)];
          // Schedule an update for the random aircraft
          //setTimeout(() => updateAircraftCoordinates(randomId), 0);
          updateAircraftCoordinates(randomId)
        }
        return prevAircrafts;
      }
    });
  }, [updateAircraftCoordinates]);

  // Timer 1: Updates AC1 and AC2
  useEffect(() => {
    const timer1 = setInterval(() => {
      updateAircraftCoordinates('AC1');
      updateAircraftCoordinates('AC2');
    }, 1000); // Update every second

    return () => clearInterval(timer1);
  }, [updateAircraftCoordinates]);

  // Timer 2: Updates AC3
  useEffect(() => {
    const timer2 = setInterval(() => {
      updateAircraftCoordinates('AC3');
    }, 3000); // Update every 3 seconds

    return () => clearInterval(timer2);
  }, [updateAircraftCoordinates]);

  // WebSocket connection function
  const connectWebSocket = useCallback(() => {
    const ws = new WebSocket('ws://localhost:9300');
    
    ws.onopen = () => {
      console.log('Connected to WebSocket server');
      setSocket(ws);
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
    };

    ws.onmessage = (event) => {
      handleAircraftUpdate(event.data);
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
    };

    ws.onclose = () => {
      console.log('Disconnected from WebSocket server');
      setSocket(null);

      // Implement reconnection with exponential backoff
      const reconnectDelay = 1000;
      console.log(`Attempting to reconnect in ${reconnectDelay}ms`);
      reconnectTimeoutRef.current = setTimeout(() => {
        connectWebSocket();
      }, reconnectDelay);
    };

    return ws;
  }, [handleAircraftUpdate]);

  // Connect to WebSocket server
  useEffect(() => {
    const ws = connectWebSocket();

    return () => {
      if (ws) {
        ws.close();
      }
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
        reconnectTimeoutRef.current = null;
      }
    };
  }, [connectWebSocket]);

  const Row = ({ index, style }) => {
    const aircraft = Array.from(aircrafts.values())[index];
    let idClass = '';
    
    if (aircraft.id === 'AC3') {
      idClass = 'aircraft-id-ac3';
    } else if (parseInt(aircraft.id) >= aircraftUpdatId) {
      idClass = 'aircraft-id-new';
    }

    return (
      <div className="aircraft-row" style={style}>
        <span className={idClass}>{aircraft.id}</span>: {aircraft.name}: ({aircraft.lon.toFixed(2)}, {aircraft.lat.toFixed(2)}) [Updates: {aircraft.updateCount}]
      </div>
    );
  };

  return (
    <div className="aircraft-tracker">
      <List 
        className="aircraft-list"
        height={600}
        itemCount={aircrafts.size}
        itemSize={30}
      >
        {Row}
      </List>
    </div>
  );
};

export default AircraftTracker;