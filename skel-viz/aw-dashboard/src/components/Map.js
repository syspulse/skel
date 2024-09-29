import React, { useState, useCallback, useEffect, useMemo, useRef } from 'react';
import Map, { Source, Layer, Marker } from 'react-map-gl';
import * as turf from '@turf/turf';
import { Area } from '../core/Area.ts';

const MAPBOX_TOKEN = process.env.REACT_APP_MAPBOX_ACCESS_TOKEN;

function HexagonMap({ 
  onHexagonSelect, 
  hexagons, 
  setHexagons, 
  selectedHexagon, 
  mapCenter, 
  aircraft, 
  selectedAircraft, 
  onAircraftSelect,
  viewState,
  setViewState
}) {
  const [pathKey, setPathKey] = useState(0);

  // const [viewState, setViewState] = useState({
  //   longitude: -122.4194,
  //   latitude: 37.7749,
  //   zoom: 10
  // });
  const [cursor, setCursor] = useState('grab');
  const mapRef = useRef();
  const sourceRef = useRef(null);

  useEffect(() => {
    if (mapCenter) {
      setViewState(prevState => ({
        ...prevState,
        longitude: mapCenter[0],
        latitude: mapCenter[1],
        //zoom: 13, // You can adjust this zoom level as needed
        transitionDuration: 3000 // Smooth transition in milliseconds
      }));
    }
  }, [mapCenter]);

  const createHexagon = useCallback((center, id, radius) => {
    const options = { steps: 6, units: 'kilometers' };
    const hexagon = turf.circle(center, radius, options);
    
    const area = new Area(id, center, radius);
    hexagon.properties = area;

    return hexagon;
  }, []);

  const onClick = (event) => {
    const features = event.features || [];
    
    if (features.length > 0) {
      const feature = features[0];
      
      if (feature.layer.id === 'hexagon-layer') {
        // Hexagon was clicked
        const selectedArea = feature.properties;
        onHexagonSelect(selectedArea);
        onAircraftSelect(null); // Deselect aircraft when hexagon is selected
      } else if (feature.layer.id === 'aircraft-layer') {
        // Aircraft was clicked
        const clickedAircraft = aircraft.find(a => a.id === feature.properties.id);
        if (clickedAircraft) {
          onAircraftSelect(clickedAircraft);
          onHexagonSelect(null); // Deselect hexagon when aircraft is selected
        }
      }
    } else {
      // Clicked on empty space
      onHexagonSelect(null);
      onAircraftSelect(null);
    }
  };

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

  const aircraftData = useMemo(() => {    
    return {
      type: 'FeatureCollection',
      features: aircraft.map(plane => {
        return ({
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: [plane.longitude, plane.latitude]
        },
        properties: {
          id: plane.id,
          angle: plane.bearing //Math.random() * 360,
        }
      })})
    };
  }, [aircraft]);
  
  useEffect(() => {
    if (!selectedAircraft) {
      setPathData(null);
      return;
    }
    
    const features = [];
    for (let i = 1; i < selectedAircraft.recentPositions.length; i++) {
      const index = Math.floor(i / (selectedAircraft.recentPositions.length / blueGradient.length));
      features.push({
        type: 'Feature',
        properties: {
          segmentIndex: index
        },
        geometry: {
          type: 'LineString',
          coordinates: [
            selectedAircraft.recentPositions[i - 1],
            selectedAircraft.recentPositions[i]
          ]
        }
      });
    }

    const newPathData = {
      type: 'FeatureCollection',
      features: features
    };


    setPathData(newPathData);

    // Update the existing source if it exists
    if (sourceRef.current) {
      sourceRef.current.setData(newPathData);
    }

  }, [aircraft, selectedAircraft]);

  // useEffect(() => {
  //   if (!selectedAircraft) {
  //     setPathData(null);
  //     return;
  //   }
    
  //   const newPathData = {
  //     type: 'FeatureCollection',
  //     features: [{
  //       type: 'Feature',
  //       properties: {
  //         segmentIndex: 0
  //       },
  //       geometry: {
  //         type: 'LineString',
  //         coordinates: selectedAircraft.recentPositions
  //       }
  //     }]
  //   };

  //   setPathData(newPathData);

  //   // Update the existing source if it exists
  //   if (sourceRef.current) {
  //     sourceRef.current.setData(newPathData);
  //   }

  // }, [aircraft, selectedAircraft]);

  // useEffect(() => {
  //   if (!selectedAircraft || selectedAircraft.recentPositions.length < 2) {
  //     setPathData(null);
  //     return;
  //   }

  //   const features = [];
  //   for (let i = 1; i < selectedAircraft.recentPositions.length; i++) {
  //     features.push({
  //       type: 'Feature',
  //       properties: {
  //         segmentIndex: i % blueGradient
  //       },
  //       geometry: {
  //         type: 'LineString',
  //         coordinates: [
  //           selectedAircraft.recentPositions[i - 1],
  //           selectedAircraft.recentPositions[i]
  //         ]
  //       }
  //     });
  //   }

  //   const newPathData = {
  //     type: 'FeatureCollection',
  //     features: features
  //   };

  //   setPathData(newPathData);

  //   if (sourceRef.current) {
  //     sourceRef.current.setData(newPathData);
  //   }

  // }, [selectedAircraft,selectedAircraft]);

  const onSourceLoad = (e) => {
    sourceRef.current = e.target.getSource('selected-aircraft-path');
  };

  const [pathData, setPathData] = useState(null);

  const blueGradient = [
    "#0087FF",  // Darkest blue
    "#339FFF",
    "#66B7FF",
    "#99CFFF",
    "#CCE7FF"   // Lightest blue
  ];


  return (
    <Map
      {...viewState}
      onMove={evt => setViewState(evt.viewState)}
      style={{width: '100%', height: '100%'}}
      mapStyle="mapbox://styles/mapbox/light-v10"
      mapboxAccessToken={MAPBOX_TOKEN}
      interactiveLayerIds={['hexagon-layer','aircraft-layer']}
      onClick={onClick}
      onDblClick={onDblClick}
      onMouseEnter={onMouseEnter}
      onMouseLeave={onMouseLeave}
      cursor={cursor}
      doubleClickZoom={false}
      ref={mapRef}
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
        
      {/* {selectedHexagon && (
        <Source type="geojson" data={selectedHexagon}> */}
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
            
      <Source type="geojson" data={aircraftData}>
        <Layer
          id="aircraft-layer"
          type="symbol"
          layout={{
            'icon-image': 'airport-15',
            'icon-size': 1.5,
            'icon-rotate': ['get', 'angle'],
            'icon-allow-overlap': true
          }}
        />
      </Source>
      
      <Source 
        id="selected-aircraft-path"
        type="geojson" 
        data={pathData || { type: 'FeatureCollection', features: [] }}
        onLoad={onSourceLoad}
      >
        <Layer
          id="selected-aircraft-path-layer"
          type="line"
          layout={{
            'line-cap': 'round',
            'line-join': 'round'
          }}
          paint={{
            'line-width': 3,
            'line-color': [
              'match',
              ['get', 'segmentIndex'],
              0, blueGradient[0],
              1, blueGradient[1],
              2, blueGradient[2],
              3, blueGradient[3],
              blueGradient[4]  // default color for any other value
            ]
          }}
        />
      </Source>

    </Map>
  );
}

export default HexagonMap;
