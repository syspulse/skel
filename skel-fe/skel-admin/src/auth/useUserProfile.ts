import { useAuth } from './useAuth';
import { mergeClaims } from './providers/claims';

/** User profile + JWT / UserInfo data derived from the active auth session. */
export function useUserProfile() {
  const { user, token, tokenParsed, userInfo, isAuthenticated, isLoading } = useAuth();

  const profileClaims = mergeClaims(tokenParsed, userInfo);

  return {
    profile: user,
    token,
    tokenParsed,
    userInfo,
    profileClaims,
    isAuthenticated,
    isLoading,
  };
}
