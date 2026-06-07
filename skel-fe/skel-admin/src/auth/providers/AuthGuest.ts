import { guestProfile } from '../userProfile';
import type { AuthProviderAdapter, AuthSession } from './types';

export class AuthGuest implements AuthProviderAdapter {
  readonly authType = 'guest' as const;

  async refreshSession(): Promise<AuthSession> {
    return {
      token: null,
      tokenParsed: null,
      userInfo: null,
      user: guestProfile(),
    };
  }

  login(): void {
    /* guest mode — no redirect */
  }

  logout(): void {
    /* no-op */
  }
}

export const authGuest = new AuthGuest();
