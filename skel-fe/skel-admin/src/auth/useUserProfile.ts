import { useAuth } from './useAuth';
import { parseJwtPayload } from './userProfile';

/** User profile + JWT data derived from the active auth session. */
export function useUserProfile() {
  const { user, token, tokenParsed, isAuthenticated, isLoading } = useAuth();

  const parsed =
    tokenParsed ?? (token ? parseJwtPayload(token) : null);

  return {
    profile: user,
    token,
    tokenParsed: parsed,
    isAuthenticated,
    isLoading,
  };
}
