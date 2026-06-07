import { getSkelAuthLoginUrl } from '../authConfig';
import { buildProfile, parseJwtPayload } from './claims';
import type { AuthProviderAdapter, AuthSession } from './types';

const SKEL_TOKEN_KEY = 'skel_auth_token';

/** Skel-auth OAuth — token stored after callback (future); profile from JWT claims. */
export class AuthSkel implements AuthProviderAdapter {
  readonly authType = 'skel' as const;

  async refreshSession(): Promise<AuthSession | null> {
    const token = sessionStorage.getItem(SKEL_TOKEN_KEY);
    if (!token) return null;

    const tokenParsed = parseJwtPayload(token);
    return {
      token,
      tokenParsed,
      userInfo: tokenParsed,
      user: buildProfile('skel', tokenParsed, tokenParsed),
    };
  }

  login(): void {
    window.location.href = getSkelAuthLoginUrl();
  }

  logout(): void {
    sessionStorage.removeItem(SKEL_TOKEN_KEY);
  }

  /** Persist token received from skel-auth callback. */
  static storeToken(token: string): void {
    sessionStorage.setItem(SKEL_TOKEN_KEY, token);
  }
}

export const authSkel = new AuthSkel();
