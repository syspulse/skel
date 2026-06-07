import React, { useState } from 'react';
import { IconUser } from '../components/Icons';

interface UserAvatarProps {
  avatarUrl?: string;
  name: string;
  size?: number;
  className?: string;
}

export function UserAvatar({ avatarUrl, name, size = 24, className = '' }: UserAvatarProps) {
  const [failed, setFailed] = useState(false);

  if (avatarUrl && !failed) {
    return (
      <img
        src={avatarUrl}
        alt={name}
        width={size}
        height={size}
        className={`rounded-full object-cover shrink-0 ${className}`}
        style={{ width: size, height: size }}
        onError={() => setFailed(true)}
      />
    );
  }

  return (
    <span
      className={`inline-flex items-center justify-center shrink-0 text-muted-foreground ${className}`}
      style={{ width: size, height: size }}
    >
      <IconUser size={Math.round(size * 0.75)} />
    </span>
  );
}
