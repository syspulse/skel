import React, { useState } from 'react';
import TopBar from './components/TopBar';
import PropertyPanel from './components/PropertyPanel';
import HexagonMap from './components/Map';
import 'mapbox-gl/dist/mapbox-gl.css';
import './App.css';

function App() {
  const [selectedHexagon, setSelectedHexagon] = useState(null);

  return (
    <div className="app">
      <TopBar />
      <div className="main-content">
        <HexagonMap onHexagonSelect={setSelectedHexagon} />
        <PropertyPanel hexagon={selectedHexagon} />
      </div>
    </div>
  );
}

export default App;
