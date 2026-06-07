import { useMemo } from 'react';
import { extractAvatarUrl, mergeClaims } from './providers/claims';
import { useAuth } from './useAuth';

/** Resolved avatar URL from profile or merged JWT / UserInfo claims. */
export function useAvatarUrl(): string | undefined {
  const { user, tokenParsed, userInfo } = useAuth();

  return useMemo(() => {
    if (user?.avatarUrl) return user.avatarUrl;
    const claims = mergeClaims(tokenParsed, userInfo);
    return claims ? extractAvatarUrl(claims) : undefined;
  }, [user?.avatarUrl, tokenParsed, userInfo]);
}
