import React from 'react';

interface SidebarProps {
  onAddNode: () => void;
}

const Sidebar: React.FC<SidebarProps> = ({ onAddNode }) => {
  return (
    <div style={{ padding: '10px', borderRight: '1px solid #ccc' }}>
      <button onClick={onAddNode}>+</button>
    </div>
  );
};

export default Sidebar;
