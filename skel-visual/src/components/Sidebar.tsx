import React from 'react';
import './DiagramEditor.css';

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

  return (
    <div className="sidebar">
      <button className="sidebar-button add" onClick={onAddNode}>+</button>
      <button className="sidebar-button" onClick={onSave}>Save</button>
      <button className="sidebar-button" onClick={onRestore}>Restore</button>
      <button className="sidebar-button" onClick={onClearAll}>Clear All</button>
      <button className="sidebar-button" onClick={onExport}>Export</button>
      <button className="sidebar-button" onClick={triggerFileInput}>
        Import
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
