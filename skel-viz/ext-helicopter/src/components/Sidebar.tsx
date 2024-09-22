import React from 'react';
import './DiagramEditor.css';
import { FiPlus, FiSave, FiRotateCcw, FiTrash2, FiUpload, FiDownload, FiPlay, FiSquare, FiRefreshCcw } from 'react-icons/fi';
import { FcSearch, FcElectricalSensor} from 'react-icons/fc';
import { TbTopologyStar3 } from "react-icons/tb";

interface SidebarProps {
  onAddNode: () => void;
  onSave: () => void;
  onRestore: () => void;
  onClearAll: () => void;
  onExport: (file: File) => void;
  onImport: (file: File) => void;
  onRefresh: () => void;
  onPopulate: () => void;
  projectId: string; // Add this prop
}

const Sidebar: React.FC<SidebarProps> = ({ 
  onAddNode, 
  onSave, 
  onRestore, 
  onClearAll, 
  onExport, 
  onImport, 
  onRefresh, 
  onPopulate,
  projectId // Add this prop
}) => {
  const fileInputRef = React.useRef<HTMLInputElement>(null);
  const fileOutputRef = React.useRef<HTMLInputElement>(null);

  const handleImport = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      onImport(file);
    }
  };

  const triggerFileInput = () => {
    fileInputRef.current?.click();
  };

  // const handleExport = (event: React.ChangeEvent<HTMLInputElement>) => {
  //   const file = event.target.files?.[0];
  //   if (file) {
  //     onExport(file);
  //   }
  // };

  const triggerFileOutput = () => {
    const fileName = `project_${projectId}.json`;
    const file = new File([], fileName, { type: 'application/json' });    
    onExport(file);
  };


  const buttonLabels = {
    addNode: 'Add',
    save: 'Save',
    restore: 'Restore',
    clearAll: 'Clear',
    export: 'Export...',
    import: 'Import...',    
    refresh: 'Telemetry',
    populate: 'Topology'
  };


  return (
    <div className="sidebar">
      <button className="sidebar-button" onClick={onAddNode}>
        <div className="button-content">
          <FiPlus />
          <span>{buttonLabels.addNode}</span>
        </div>
      </button>
      <button className="sidebar-button" onClick={onSave}>
        <div className="button-content">
          <FiSave />
          <span>{buttonLabels.save}</span>
        </div>
      </button>
      <button className="sidebar-button" onClick={onRestore}>
        <div className="button-content">
          <FiRotateCcw />
          <span>{buttonLabels.restore}</span>
        </div>
      </button>
      <button className="sidebar-button" onClick={onClearAll}>
        <div className="button-content">
          <FiTrash2 />
          <span>{buttonLabels.clearAll}</span>
        </div>
      </button>
      <button className="sidebar-button" onClick={triggerFileOutput}>
        <div className="button-content">
          <FiUpload />
          <span>{buttonLabels.export}</span>
          {/* <input
            type="file"
            ref={fileOutputRef}
            style={{ display: 'none' }}
            accept=".json"            
            onChange={handleExport}
          /> */}
        </div>
      </button>
      <button className="sidebar-button" onClick={triggerFileInput}>
        <div className="button-content">
          <FiDownload />
          <span>{buttonLabels.import}</span>
        </div>
        <input
          type="file"
          ref={fileInputRef}
          style={{ display: 'none' }}
          accept=".json"
          onChange={handleImport}
        />
      </button>
      <button className="sidebar-button" onClick={onRefresh}>
        <div className="button-content">
          <FcElectricalSensor />
          <span>{buttonLabels.refresh}</span>
        </div>
      </button>
      <button className="sidebar-button" onClick={onPopulate}>
        <div className="button-content">
          <TbTopologyStar3 />
          <span>{buttonLabels.populate}</span>
        </div>
      </button>
    </div>
  );
};

export default Sidebar;
