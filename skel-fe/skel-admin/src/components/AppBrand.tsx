import React, { useEffect, useState } from 'react';
import { IconLamp } from './Icons';
import { isInlineSvg, resolveLogoSrc } from '../theme/branding';

export interface AppLogoProps {
  logoUrl: string;
  size?: number;
  className?: string;
  iconClassName?: string;
}

/** Renders custom logo (URL, data URI, or inline SVG) or the default lamp icon. */
export function AppLogo({ logoUrl, size = 22, className = '', iconClassName = '' }: AppLogoProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [logoUrl]);

  if (!logoUrl.trim() || failed) {
    return <IconLamp size={size} className={iconClassName || className} />;
  }

  const s = logoUrl.trim();
  if (isInlineSvg(s)) {
    return (
      <span
        className={`inline-flex items-center justify-center shrink-0 [&>svg]:w-full [&>svg]:h-full ${className}`}
        style={{ width: size, height: size }}
        dangerouslySetInnerHTML={{ __html: s }}
      />
    );
  }

  return (
    <img
      src={resolveLogoSrc(s)}
      alt=""
      width={size}
      height={size}
      className={`object-contain shrink-0 ${className}`}
      onError={() => setFailed(true)}
    />
  );
}

export interface AppBrandMarkProps {
  appName: string;
  logoUrl: string;
  size?: number;
  nameClassName?: string;
  iconClassName?: string;
}

/** Logo + app name (header, login screen, etc.). */
export function AppBrandMark({
  appName,
  logoUrl,
  size = 22,
  nameClassName = 'text-lg tracking-wide',
  iconClassName = '',
}: AppBrandMarkProps) {
  return (
    <div className="flex items-center gap-2 min-w-0">
      <AppLogo logoUrl={logoUrl} size={size} iconClassName={iconClassName} />
      <span className={`truncate ${nameClassName}`}>{appName}</span>
    </div>
  );
}
