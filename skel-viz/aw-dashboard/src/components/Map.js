import React, { useState, useCallback, useMemo, useEffect } from 'react';
import Map, { Source, Layer } from 'react-map-gl';
import * as turf from '@turf/turf';
import { Area } from '../core/Area.ts';

const MAPBOX_TOKEN = process.env.REACT_APP_MAPBOX_ACCESS_TOKEN;

function HexagonMap({ onHexagonSelect, hexagons, setHexagons }) {
  const [viewState, setViewState] = useState({
    longitude: -122.4,
    latitude: 37.8,
    zoom: 11
  });

  const [selectedHexagon, setSelectedHexagon] = useState(null);
  const [cursor, setCursor] = useState('grab');

  const createHexagon = useCallback((center, id, radius) => {
    const options = { steps: 6, units: 'kilometers' };
    const hexagon = turf.circle(center, radius, options);
    
    const area = new Area(id, center, radius);
    hexagon.properties = area;

    return hexagon;
  }, []);

  const initialHexagons = useMemo(() => {
    const bbox = [-122.5, 37.7, -122.3, 37.9];
    const hexagonFeatures = [];

    for (let i = 0; i < 10; i++) {
      const center = turf.randomPosition(bbox);
      const radius = Math.random() * 1.5 + 0.5; // Random between 0.5 and 10
      hexagonFeatures.push(createHexagon(center, i, radius));
    }

    return turf.featureCollection(hexagonFeatures);
  }, [createHexagon]);

  // Use hexagons state if it exists, otherwise use initialHexagons
  const [localHexagons, setLocalHexagons] = useState(null);

  useEffect(() => {
    if (hexagons) {
      setLocalHexagons(hexagons);
    } else {
      setLocalHexagons(initialHexagons);
      setHexagons(initialHexagons);
    }
  }, [hexagons, initialHexagons, setHexagons]);

  const onClick = useCallback((event) => {
    const features = event.features || [];
    if (features.length > 0) {
      const properties = features[0].properties;
      const center = turf.center(features[0]);
      properties.longitude = center.geometry.coordinates[0];
      properties.latitude = center.geometry.coordinates[1];
      setSelectedHexagon(features[0]);
      onHexagonSelect(properties);
    } else {
      setSelectedHexagon(null);
      onHexagonSelect(null);
    }
  }, [onHexagonSelect]);

  const onDblClick = useCallback((event) => {
    event.preventDefault();
    const { lngLat } = event;
    
    // Ensure lngLat contains valid numbers
    if (isNaN(lngLat.lng) || isNaN(lngLat.lat)) {
      console.error('Invalid coordinates:', lngLat);
      return;
    }

    const clickedPoint = [lngLat.lng, lngLat.lat];
    const radius = Math.random() * 1.5 + 0.5; // Random between 0.5 and 10

    // Create a new hexagon
    const newHexagon = createHexagon(clickedPoint, hexagons.features.length, radius);
    
    setHexagons(prevHexagons => {
      const newFeatures = [...prevHexagons.features, newHexagon];
      return turf.featureCollection(newFeatures);
    });
    setSelectedHexagon(newHexagon);
    onHexagonSelect(newHexagon.properties);
  }, [createHexagon, hexagons, setHexagons, onHexagonSelect]);

  const onMouseEnter = useCallback(() => setCursor('pointer'), []);
  const onMouseLeave = useCallback(() => setCursor('grab'), []);

  return (
    <Map
      {...viewState}
      onMove={evt => setViewState(evt.viewState)}
      style={{width: '100%', height: '100%'}}
      mapStyle="mapbox://styles/mapbox/light-v10"
      mapboxAccessToken={MAPBOX_TOKEN}
      interactiveLayerIds={['hexagon-layer']}
      onClick={onClick}
      onDblClick={onDblClick}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      cursor={cursor}
      doubleClickZoom={false}
    >
      <Source type="geojson" data={localHexagons}>
        <Layer
          id="hexagon-layer"
          type="fill"
          paint={{
            'fill-color': [
              'interpolate',
              ['linear'],
              ['get', 'counter'],
              0, '#F2F12D',
              2500, '#EED322',
              5000, '#E6B71E',
              7500, '#DA9C20',
              10000, '#CA8323'
            ],
            'fill-opacity': 0.7
          }}
        />
        <Layer
          id="hexagon-outline"
          type="line"
          paint={{
            'line-color': '#000000',
            'line-width': 1
          }}
        />
        <Layer
          id="hexagon-label"
          type="symbol"
          layout={{
            'text-field': ['get', 'name'],
            'text-anchor': 'center',
            'text-offset': [0, 2.0],  // Changed to 1.6
            'text-size': 10
          }}
          paint={{
            'text-color': '#000000',
            'text-halo-color': '#FFFFFF',
            'text-halo-width': 1
          }}
        />
      </Source>
      {selectedHexagon && (
        <Source type="geojson" data={selectedHexagon}>
          <Layer
            id="selected-hexagon"
            type="line"
            paint={{
              'line-color': '#4FC3F7',
              'line-width': 3
            }}
          />
        </Source>
      )}
    </Map>
  );
}

export default HexagonMap;
