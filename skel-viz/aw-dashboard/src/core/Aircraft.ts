import * as turf from '@turf/turf';

export class Aircraft {
  id: number;
  callsign: string;
  icao: string;
  velocity: number;
  longitude: number;
  latitude: number;
  angle: number;
  bearing: number;

  constructor(id: number, callsign: string, icao: string, velocity: number, longitude: number, latitude: number) {
    this.id = id;
    this.callsign = callsign;
    this.icao = icao;
    this.velocity = velocity;
    this.longitude = longitude;
    this.latitude = latitude;
    this.angle = Math.random() * 360; // Random initial angle
  }

}

const SF_CENTER_LAT = 37.7749;
const SF_CENTER_LON = -122.4194;
const RADIUS = 0.1; // Adjust this value to change the circle size

export function planeMove(aircraft: Aircraft) {
  // Store previous position
  const prevLon = aircraft.longitude;
  const prevLat = aircraft.latitude;

  // Increase the angle by a small amount (adjust for speed)
  aircraft.angle += 1;
  aircraft.angle %= 360;

  const angleRad = aircraft.angle * (Math.PI / 180);
  
  // Calculate new position
  const newLon = SF_CENTER_LON + RADIUS * Math.cos(angleRad);
  const newLat = SF_CENTER_LAT + RADIUS * Math.sin(angleRad);

  // Calculate the bearing based on previous and new position
  aircraft.bearing = turf.bearing([prevLon, prevLat], [newLon, newLat]);
  //(Math.atan2(newLon - prevLon, newLat - prevLat) * 180 / Math.PI + 360) % 360;

  // Update position
  aircraft.longitude = newLon;
  aircraft.latitude = newLat;

  return aircraft;
}
