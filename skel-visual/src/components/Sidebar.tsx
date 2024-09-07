import React from 'react';
import './DiagramEditor.css';
import { FiPlus, FiSave, FiRotateCcw, FiTrash2, FiUpload, FiDownload, FiPlay, FiSquare } from 'react-icons/fi';

interface SidebarProps {
  onAddNode: () => void;
  onSave: () => void;
  onRestore: () => void;
  onClearAll: () => void;
  onExport: () => void;
  onImport: (file: File) => void;
}

const Sidebar: React.FC<SidebarProps> = ({ onAddNode, onSave, onRestore, onClearAll, onExport, onImport }) => {
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  const handleImport = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      onImport(file);
    }
  };

  const triggerFileInput = () => {
    fileInputRef.current?.click();
  };

  const buttonLabels = {
    addNode: 'Add',
    save: 'Save',
    restore: 'Restore',
    clearAll: 'Clear',
    export: 'Export...',
    import: 'Import...',
    startSimulation: 'Start Sim',
    stopSimulation: 'Stop Sim'
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
      <button className="sidebar-button" onClick={onExport}>
        <div className="button-content">
          <FiUpload />
          <span>{buttonLabels.export}</span>
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
    </div>
  );
};

export default Sidebar;
