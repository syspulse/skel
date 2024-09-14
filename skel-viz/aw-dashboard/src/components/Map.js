import React, { useState, useCallback, useMemo } from 'react';
import Map, { Source, Layer } from 'react-map-gl';
import * as turf from '@turf/turf';

const MAPBOX_TOKEN = process.env.REACT_APP_MAPBOX_ACCESS_TOKEN;

function HexagonMap({ onHexagonSelect }) {
  const [viewState, setViewState] = useState({
    longitude: -122.4,
    latitude: 37.8,
    zoom: 11
  });

  const [selectedHexagon, setSelectedHexagon] = useState(null);
  const [cursor, setCursor] = useState('grab');

  const createHexagon = useCallback((center, id) => {
    const radius = 0.5; // 0.5 km radius
    const options = { steps: 6, units: 'kilometers' };
    const hexagon = turf.circle(center, radius, options);
    
    hexagon.properties = {
      id: id,
      name: `Hex ${id}`,
      population: Math.floor(Math.random() * 10000),
      area: Math.floor(Math.random() * 100) / 10, // Random area between 0 and 10 km²
      longitude: center[0],
      latitude: center[1]
    };

    return hexagon;
  }, []);

  const initialHexagons = useMemo(() => {
    const bbox = [-122.5, 37.7, -122.3, 37.9];
    const hexagonFeatures = [];

    for (let i = 0; i < 10; i++) {
      const center = turf.randomPosition(bbox);
      hexagonFeatures.push(createHexagon(center, i));
    }

    return turf.featureCollection(hexagonFeatures);
  }, [createHexagon]);

  const [hexagons, setHexagons] = useState(initialHexagons);

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
    const clickedPoint = [lngLat.lng, lngLat.lat];

    // Create a new hexagon
    const newHexagon = createHexagon(clickedPoint, hexagons.features.length);
    setHexagons(prevHexagons => {
      const newFeatures = [...prevHexagons.features, newHexagon];
      return turf.featureCollection(newFeatures);
    });
    setSelectedHexagon(newHexagon);
    onHexagonSelect(newHexagon.properties);
  }, [createHexagon, hexagons.features.length, onHexagonSelect]);

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
      <Source type="geojson" data={hexagons}>
        <Layer
          id="hexagon-layer"
          type="fill"
          paint={{
            'fill-color': [
              'interpolate',
              ['linear'],
              ['get', 'population'],
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
