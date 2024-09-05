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
  const handleImport = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      onImport(file);
    }
  };

  return (
    <div className="sidebar">
      <button className="sidebar-button add" onClick={onAddNode}>+</button>
      <button className="sidebar-button" onClick={onSave}>Save</button>
      <button className="sidebar-button" onClick={onRestore}>Restore</button>
      <button className="sidebar-button" onClick={onClearAll}>Clear</button>
      <button className="sidebar-button" onClick={onExport}>Export...</button>
      <input type="file" accept=".json" onChange={handleImport} id="import-json" className="file-input" />
      <label htmlFor="import-json" className="sidebar-button import-label">
        Import...
      </label>
    </div>
  );
};

export default Sidebar;
