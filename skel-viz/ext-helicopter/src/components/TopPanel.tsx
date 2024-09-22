import React, { useCallback, useEffect, useRef, useState } from 'react';
import './TopPanel.css'; // Ensure this CSS file is created
import TopMenu from './TopMenu';
import { getProjects, getTenants } from '../extractor';

interface TopPanelProps {
  onLogin: () => void;
  onSearch: (searchText: string) => void;      
  onRefreshFreq: (refreshFreq: number) => void;  
  searchInputRef: React.RefObject<HTMLInputElement>;

  onProjectId: (projectId: string) => void;
  onTenantId: (tenantId: string) => void;
}

const TopPanel: React.FC<TopPanelProps> = ({ onLogin,onSearch,onProjectId,onTenantId,onRefreshFreq,searchInputRef}) => {
  
  const [searchText, setSearchText] = useState('');

  const [projects, setProjects] = useState(['645']);
  const [projectId, setProjectId] = useState<string>('645');

  const [tenantIds, setTenantIds] = useState<string[]>(['490']);
  const [selectedTenantId, setSelectedTenantId] = useState<string>('');
  
  const [refreshFreq, setRefreshFreq] = useState(60000);

  const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;    
    setSearchText(value);
    onSearch(value); // Call the onSearch prop to filter nodes
  };

  useEffect(() => {
    const loadTenantIds = async () => {
      const tenants = await getTenants();
      const tenantsNames = tenants.map((t:any) => `${t.id}-${t.name}`);
      setTenantIds(tenantsNames);
      if (tenantsNames.length > 0) {
        setSelectedTenantId(tenantsNames[0]);
        onTenantId(tenantsNames[0]);
      }
    };

    loadTenantIds();
  }, [setTenantIds]);

  const handleProjectIdChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newProjectId = e.target.value;
    setProjectId(newProjectId);
    onProjectId(newProjectId);
  };

  const handleTenantIdChange = useCallback((event: React.ChangeEvent<HTMLSelectElement>) => {
    const newTenantId = event.target.value;
    setSelectedTenantId(newTenantId);
    onTenantId(newTenantId);
  
    const loadProjects = async () => {
      const tid = newTenantId.split('-')[0];
      const fetchedProjects = await getProjects(tid);
      
      if (fetchedProjects && fetchedProjects.length > 0) {
        const projectIds = fetchedProjects.map((p: any) => `${p.id}-${p.name}`);
        setProjects(projectIds);
        // Don't set projectId or call onProjectId here
      } else {
        setProjects([]);
      }
    };
  
    loadProjects();
  }, [onTenantId]);
  
  // Separate useEffect to handle projectId updates when projects change
  useEffect(() => {
    if (projects.length > 0 && !projectId) {
      setProjectId(projects[0]);
      onProjectId(projects[0]);
    } else if (projects.length === 0) {
      setProjectId('');
      onProjectId('');
    }
  }, [projects, projectId, onProjectId]);

  const handleRefreshFreqChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const freq = Number(event.target.value);
    setRefreshFreq(freq);
    onRefreshFreq(freq);
  };

  return (
    <div className="top-panel">
      <div className="logo-container">
        <img src="/assets/diagram.png" alt="" className="logo-image" /> {/* Update the path to your logo image */}
        <div className="logo">blockpulse</div>
      </div>
      <input
        ref = {searchInputRef}
        type="text"
        placeholder={`Press \u002F ...`}
        className="search-input"
        value={searchText}
        onChange={handleSearchChange} // Update search text on change
      />

      <div className="options-container">
        
      </div>

      <div className="options-container">
      <label htmlFor="tenantId">Tenant:</label>        
        <select
          id="tenantId"
          value={selectedTenantId}
          onChange={handleTenantIdChange}
          // onDoubleClick={toggleTenantIdEdit}
        >
          {tenantIds.map((id) => (
            <option key={id} value={id}>
              {id}
            </option>
          ))}
        </select> 

        <label htmlFor="projectId">Project:</label>
        <select 
          value={projectId} 
          onChange={(e: React.ChangeEvent<HTMLSelectElement>) => handleProjectIdChange(e)}
          disabled={!tenantIds}
        >          
          {projects.map((project) => (
            <option key={project}>
              {project}
            </option>
          ))}
        </select>

        <label htmlFor="refreshFreq">Refresh:</label>
        <input
          type="text"
          id="refreshFreq"
          value={refreshFreq}
          onChange={handleRefreshFreqChange}
          className="option-input-number"
        />
      </div>
      <TopMenu onLogin={onLogin} />
    </div>
  );
};

export default TopPanel;