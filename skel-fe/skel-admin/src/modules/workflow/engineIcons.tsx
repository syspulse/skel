import type { IconProps } from '../../components/Icons';
import { IconTemporal, IconArrowRight } from '../../components/Icons';

// Static map of WorkflowConfig.meta.engine -> panel-link icon. Unknown/absent engines fall back to
// the generic [->] (IconArrowRight). Keys are matched case-insensitively (see `engineIcon`).
const ENGINE_ICONS: Record<string, (props: IconProps) => JSX.Element> = {
  temporal: IconTemporal,
};

/** Icon for a `meta.engine` value; [->] (IconArrowRight) when the engine is unknown or unset. */
export function engineIcon(engine?: string): (props: IconProps) => JSX.Element {
  const key = engine?.trim().toLowerCase();
  return (key && ENGINE_ICONS[key]) || IconArrowRight;
}
