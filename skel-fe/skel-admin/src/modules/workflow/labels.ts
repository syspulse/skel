// Single source of truth for the small id/label chips shown on graph nodes, the side menu and the
// editor panel. Kept as inline styles so both React elements and SVG-ish nodes can share them.
import type { CSSProperties } from 'react';

// Default label: dark-gray background, white font.
export const LABEL_DEFAULT: CSSProperties = { background: '#374151', color: '#ffffff' };
// DetectorSchema / WorkflowSchema id: light gray.
export const LABEL_SCHEMA: CSSProperties = { background: '#e2e8f0', color: '#475569' };
// DetectorConfig / WorkflowConfig id: uses the default (dark-gray / white).
export const LABEL_CONFIG: CSSProperties = LABEL_DEFAULT;

/** Label style for an entity id chip by kind (…config -> config style, …schema -> schema style). */
export function idLabelStyle(kind: string): CSSProperties {
  return kind.includes('config') ? LABEL_CONFIG : LABEL_SCHEMA;
}
