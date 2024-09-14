import React, { useState, useCallback, useMemo } from 'react';
import Map, { Source, Layer } from 'react-map-gl';
import * as turf from '@turf/turf';
import { Area } from '../core/Area.ts';

const MAPBOX_TOKEN = process.env.REACT_APP_MAPBOX_ACCESS_TOKEN;

function HexagonMap({ onHexagonSelect, hexagons, setHexagons, selectedHexagon }) {
  const [viewState, setViewState] = useState({
    longitude: -122.4,
    latitude: 37.8,
    zoom: 11
  });

  const [cursor, setCursor] = useState('grab');

  const createHexagon = useCallback((center, id, radius) => {
    const options = { steps: 6, units: 'kilometers' };
    const hexagon = turf.circle(center, radius, options);
    
    const area = new Area(id, center, radius);
    hexagon.properties = area;

    return hexagon;
  }, []);

  const onClick = useCallback((event) => {
    const features = event.features || [];
    if (features.length > 0) {
      const feature = features[0];
      const center = turf.center(feature);
      const selectedArea = new Area(
        feature.properties.id,
        center.geometry.coordinates,
        feature.properties.radius
      );
      // Copy all properties from the feature to the selectedArea
      Object.assign(selectedArea, feature.properties);
      onHexagonSelect(selectedArea);
    } else {
      onHexagonSelect(null);
    }
  }, [onHexagonSelect]);

  const onDblClick = useCallback((event) => {
    event.preventDefault();
    const { lngLat } = event;
    
    if (isNaN(lngLat.lng) || isNaN(lngLat.lat)) {
      console.error('Invalid coordinates:', lngLat);
      return;
    }

    const clickedPoint = [lngLat.lng, lngLat.lat];
    const radius = Math.random() * 1.5 + 0.5; // Random between 0.5 and 2.0

    const newHexagon = createHexagon(clickedPoint, hexagons ? hexagons.features.length : 0, radius);
    
    setHexagons(prevHexagons => {
      if (!prevHexagons) {
        return turf.featureCollection([newHexagon]);
      }
      const newFeatures = [...prevHexagons.features, newHexagon];
      return turf.featureCollection(newFeatures);
    });
    onHexagonSelect(newHexagon.properties);
  }, [createHexagon, hexagons, setHexagons, onHexagonSelect]);

  const onMouseEnter = useCallback(() => setCursor('pointer'), []);
  const onMouseLeave = useCallback(() => setCursor('grab'), []);

  const selectedHexagonFeature = useMemo(() => {
    if (!selectedHexagon || !hexagons) return null;
    const feature = hexagons.features.find(f => f.properties.id === selectedHexagon.id);
    return feature ? turf.feature(feature.geometry, feature.properties) : null;
  }, [selectedHexagon, hexagons]);

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
      {hexagons && (
        <Source type="geojson" data={hexagons}>
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
              'text-offset': [0, 1.6],
              'text-size': 10
            }}
            paint={{
              'text-color': '#000000',
              'text-halo-color': '#FFFFFF',
              'text-halo-width': 1
            }}
          />
        </Source>
      )}
      {selectedHexagonFeature && (
        <Source type="geojson" data={selectedHexagonFeature}>
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
