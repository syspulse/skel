import type { LoginMethod } from '../authConfig';
import type { AuthType } from '../userProfile';
import { authGoogle } from './AuthGoogle';
import { authGuest } from './AuthGuest';
import { authKeycloak } from './AuthKeycloak';
import { authSkel } from './AuthSkel';
import type { AuthProviderAdapter } from './types';

export type { AuthProviderAdapter, AuthSession } from './types';
export { mergeClaims, parseJwtPayload, buildProfile, profileFromClaims } from './claims';
export { getKeycloakInstance, getKeycloakRef, AuthKeycloak, authKeycloak } from './AuthKeycloak';
export { AuthGoogle, authGoogle } from './AuthGoogle';
export { AuthSkel, authSkel } from './AuthSkel';
export { AuthGuest, authGuest } from './AuthGuest';

const providers: Record<Exclude<AuthType, 'guest'>, AuthProviderAdapter> = {
  keycloak: authKeycloak,
  google: authGoogle,
  skel: authSkel,
};

export function getAuthProvider(method: LoginMethod | AuthType | null): AuthProviderAdapter {
  if (!method || method === 'guest') return authGuest;
  return providers[method] ?? authKeycloak;
}

export function loginMethodToAuthType(method: LoginMethod | null): AuthType {
  if (!method || method === 'guest') return 'guest';
  return method;
}
