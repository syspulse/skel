import React, { createContext, useContext, useState } from 'react';

// Default react-flow grid distance and snap-to-grid for the Workflow editor.
// Persisted in localStorage the same way as PageSize / TimestampFormat settings.
export const DEFAULT_GRID_SIZE = 12;
export const DEFAULT_SNAP_TO_GRID = false;
const KEY_SIZE = 'workflowGridSize';
const KEY_SNAP = 'workflowSnapToGrid';

interface WorkflowGridContextType {
  gridSize: number;
  setGridSize: (size: number) => void;
  snapToGrid: boolean;
  setSnapToGrid: (snap: boolean) => void;
}

const WorkflowGridContext = createContext<WorkflowGridContextType>({
  gridSize: DEFAULT_GRID_SIZE,
  setGridSize: () => {},
  snapToGrid: DEFAULT_SNAP_TO_GRID,
  setSnapToGrid: () => {},
});

function readGridSize(): number {
  const stored = localStorage.getItem(KEY_SIZE);
  const n = stored ? parseInt(stored, 10) : NaN;
  return Number.isFinite(n) && n > 0 ? n : DEFAULT_GRID_SIZE;
}
function readSnapToGrid(): boolean {
  const stored = localStorage.getItem(KEY_SNAP);
  return stored == null ? DEFAULT_SNAP_TO_GRID : stored === 'true';
}

export function WorkflowGridProvider({ children }: { children: React.ReactNode }) {
  const [gridSize, setGridSizeState] = useState<number>(readGridSize);
  const [snapToGrid, setSnapToGridState] = useState<boolean>(readSnapToGrid);

  const setGridSize = (size: number) => {
    localStorage.setItem(KEY_SIZE, String(size));
    setGridSizeState(size);
  };
  const setSnapToGrid = (snap: boolean) => {
    localStorage.setItem(KEY_SNAP, String(snap));
    setSnapToGridState(snap);
  };

  return (
    <WorkflowGridContext.Provider value={{ gridSize, setGridSize, snapToGrid, setSnapToGrid }}>
      {children}
    </WorkflowGridContext.Provider>
  );
}

export function useWorkflowGrid() {
  return useContext(WorkflowGridContext);
}
