import React from 'react';

interface SidebarProps {
  onAddNode: () => void;
  onSave: () => void;
  onRestore: () => void;
  onClearAll: () => void; // Add this line
}

const Sidebar: React.FC<SidebarProps> = ({ onAddNode,onSave,onRestore,onClearAll }) => {
  return (
    <div style={{ padding: '10px', borderRight: '1px solid #ccc', display: 'flex', flexDirection: 'column', gap: '10px' }}>
    <button onClick={onAddNode}>+</button>
    <button onClick={onSave}>Save</button>
    <button onClick={onRestore}>Restore</button>
    <button onClick={onClearAll}>Clear</button>
  </div>
  );
};

export default Sidebar;
