import React, { useState, useEffect, useCallback, useRef } from 'react';
import { FixedSizeList as List } from 'react-window';
import './AircraftTracker.css';

const AircraftTracker = () => {
  const [aircrafts, setAircrafts] = useState(new Map([
    ['AC1', { id: 'AC1', name: 'Aircraft 1', lon: 0, lat: 0 }],
    ['AC2', { id: 'AC2', name: 'Aircraft 2', lon: 0, lat: 0 }],
    ['AC3', { id: 'AC3', name: 'Aircraft 3', lon: 0, lat: 0 }],
  ]));
  const [socket, setSocket] = useState(null);
  const reconnectTimeoutRef = useRef(null);

  // Function to update aircraft coordinates
  const updateAircraftCoordinates = useCallback((aircraftId) => {
    setAircrafts((prevAircrafts) => {
      const newAircrafts = new Map(prevAircrafts);
      const aircraft = newAircrafts.get(aircraftId);
      if (aircraft) {
        newAircrafts.set(aircraftId, {
          ...aircraft,
          lon: Math.random() * 180 - 90,
          lat: Math.random() * 180 - 90
        });
      }
      return newAircrafts;
    });
  }, []);

  // Function to handle aircraft updates based on WebSocket messages
  const handleAircraftUpdate = useCallback((message) => {
    const messageNumber = parseInt(message);
    const id = 10 + messageNumber % 3;

    if (!isNaN(messageNumber) && id !== 0) {        
      // Add new aircraft if message is a number divisible by 3
      setAircrafts(prevAircrafts => {
        const newAircrafts = new Map(prevAircrafts);
        newAircrafts.set(id, { id: id, name: `Aircraft ${message}`, lon: 0, lat: 0 });
        return newAircrafts;
      });
      // Immediately update the coordinates of the new aircraft
      updateAircraftCoordinates(id);
    } else {
      // Update a random existing aircraft
      setAircrafts(prevAircrafts => {
        const aircraftIds = Array.from(prevAircrafts.keys());
        if (aircraftIds.length > 0) {
          const randomId = aircraftIds[Math.floor(Math.random() * aircraftIds.length)];
          updateAircraftCoordinates(randomId);
        }
        return prevAircrafts;
      });
    }
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

  // // Timer 3: Updates Aircrafts with ID > 10
  // useEffect(() => {
  //   const timer3 = setInterval(() => {
  //     aircrafts.forEach((aircraft) => {
  //       if (parseInt(aircraft.id) > 10) {
  //         updateAircraftCoordinates(aircraft.id);
  //       }
  //     });
  //   }, 250); // Update every 0.5 seconds

  //   return () => clearInterval(timer3);
  // }, [aircrafts, updateAircraftCoordinates]);

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
    } else if (parseInt(aircraft.id) >= 10) {
      idClass = 'aircraft-id-new';
    }

    return (
      <div className="aircraft-row" style={style}>
        <span className={idClass}>{aircraft.id}</span>: {aircraft.name}: ({aircraft.lon.toFixed(2)}, {aircraft.lat.toFixed(2)})
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