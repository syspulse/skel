import React, { useState, useEffect } from 'react';

const AircraftTracker = () => {
  const [aircrafts, setAircrafts] = useState(new Map([
    ['AC1', { id: 'AC1', name: 'Aircraft 1', lon: 0, lat: 0 }],
    ['AC2', { id: 'AC2', name: 'Aircraft 2', lon: 0, lat: 0 }],
    ['AC3', { id: 'AC3', name: 'Aircraft 3', lon: 0, lat: 0 }],
  ]));

  // Function to update aircraft coordinates
  const updateAircraftCoordinates = (aircraftId) => {
    setAircrafts((prevAircrafts) => {
      const newAircrafts = new Map(prevAircrafts);
      const aircraft = newAircrafts.get(aircraftId);
      newAircrafts.set(aircraftId, {
        ...aircraft,
        lon: Math.random() * 180 - 90,
        lat: Math.random() * 180 - 90
      });
      return newAircrafts;
    });
  };

  // Timer 1: Updates AC1 and AC2
  useEffect(() => {
    const timer1 = setInterval(() => {
      updateAircraftCoordinates('AC1');
      updateAircraftCoordinates('AC2');
    }, 1000); // Update every 5 seconds

    return () => clearInterval(timer1);
  }, []);

  // Timer 2: Updates AC3
  useEffect(() => {
    const timer2 = setInterval(() => {
      updateAircraftCoordinates('AC3');
    }, 3000); // Update every 7 seconds

    return () => clearInterval(timer2);
  }, []);

  return (
    <div> 
        {Array.from(aircrafts.values()).map(aircraft => (
          <div key={aircraft.id}>
            {aircraft.name}: ({aircraft.lon.toFixed(2)}, {aircraft.lat.toFixed(2)})
          </div>
        ))}
      
    </div>
  );
};

export default AircraftTracker;