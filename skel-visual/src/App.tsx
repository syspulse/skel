import React, { useRef, useState } from 'react';
import ReactFlow, { Node, useNodesState, useEdgesState } from 'reactflow';
import CustomNode from './components/CustomNode';
import PropertiesPanel from './components/NodePropertyPanel';
import DiagramEditor from './components/DiagramEditor';
import TopPanel from './components/TopPanel';

const nodeTypes = {
  custom: CustomNode,
};

function App() {
  const [projectId, setProjectId] = useState('645');
  const [refreshFreq, setRefreshFreq] = useState(10000);
  const [searchText, setSearchText] = useState('');
  const searchInputRef = useRef<HTMLInputElement | null>(null);

  return (
    <div>      
      {/* <DiagramEditor/> */}
      <TopPanel 
        onLogin={() => {}} 
        onSearch={setSearchText}         
        onProjectId={setProjectId} 
        onRefreshFreq={setRefreshFreq} 
        searchInputRef={searchInputRef}
      />
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