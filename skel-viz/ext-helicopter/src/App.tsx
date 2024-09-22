import React, { useEffect, useRef, useState } from 'react';
import ReactFlow, { Node, useNodesState, useEdgesState } from 'reactflow';
import CustomNode from './components/CustomNode';
import PropertiesPanel from './components/NodePropertyPanel';
import DiagramEditor from './components/DiagramEditor';
import TopPanel from './components/TopPanel';
import { GoogleOAuthProvider } from '@react-oauth/google';
import { initKeycloak } from './keycloak';

const clientId = process.env.REACT_APP_GOOGLE_CLIENT_ID || '';

function App() {
  const [projectId, setProjectId] = useState('645');
  const [tenantId, setTenantId] = useState('490-Demo');
  const [refreshFreq, setRefreshFreq] = useState(60000);
  const [searchText, setSearchText] = useState('');
  const searchInputRef = useRef<HTMLInputElement | null>(null);

  return (
    <div className="app">    
      <GoogleOAuthProvider clientId={clientId}>
        <TopPanel 
          onLogin={() => {}} 
          onSearch={setSearchText}         
          onProjectId={setProjectId} 
          onTenantId={setTenantId} 
          onRefreshFreq={setRefreshFreq} 
          searchInputRef={searchInputRef}
        />
      </GoogleOAuthProvider>
      <DiagramEditor 
        projectId={projectId} 
        refreshFreq={refreshFreq} 
        searchText={searchText}
        searchInputRef={searchInputRef}
      />
    </div>
  );
}

export default App;