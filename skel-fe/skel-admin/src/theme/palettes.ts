export const THEME_NAMES = [
  'light','dark',
  'neutral','stone','zinc','slate','gray','mauve','olive','mist','taupe',
  'red','orange','amber','yellow','lime','green','emerald','teal','cyan',
  'sky','blue','indigo','violet','purple','fuchsia','pink','rose',
] as const;

export type Theme = typeof THEME_NAMES[number];

export const PALETTE_SWATCHES: Record<Theme, [string, string, string]> = {
  light:   ['#ffffff', '#a1a1aa', '#18181b'],
  dark:    ['#09090b', '#71717a', '#fafafa'],
  neutral: ['#fafafa', '#737373', '#0a0a0a'],
  stone:   ['#fafaf9', '#78716c', '#0c0a09'],
  zinc:    ['#fafafa', '#71717a', '#09090b'],
  slate:   ['#f8fafc', '#64748b', '#020617'],
  gray:    ['#f9fafb', '#6b7280', '#030712'],
  mauve:   ['#fafafb', '#81758b', '#161218'],
  olive:   ['#f9faf9', '#6c8072', '#101512'],
  mist:    ['#f9fafb', '#648085', '#0e1517'],
  taupe:   ['#fbfaf9', '#867865', '#17130e'],
  red:     ['#fef2f2', '#ef4444', '#450a0a'],
  orange:  ['#fff7ed', '#f97316', '#431407'],
  amber:   ['#fffbeb', '#f59e0b', '#451a03'],
  yellow:  ['#fefce8', '#eab308', '#422006'],
  lime:    ['#f7fee7', '#84cc16', '#1a2e05'],
  green:   ['#f0fdf4', '#22c55e', '#052e16'],
  emerald: ['#ecfdf5', '#10b981', '#022c22'],
  teal:    ['#f0fdfa', '#14b8a6', '#042f2e'],
  cyan:    ['#ecfeff', '#06b6d4', '#083344'],
  sky:     ['#f0f9ff', '#0ea5e9', '#082f49'],
  blue:    ['#eff6ff', '#3b82f6', '#172554'],
  indigo:  ['#eef2ff', '#6366f1', '#1e1b4b'],
  violet:  ['#f5f3ff', '#8b5cf6', '#1e1b4b'],
  purple:  ['#faf5ff', '#a855f7', '#3b0764'],
  fuchsia: ['#fdf4ff', '#d946ef', '#4a044e'],
  pink:    ['#fdf2f8', '#ec4899', '#500724'],
  rose:    ['#fff1f2', '#f43f5e', '#4c0519'],
};
