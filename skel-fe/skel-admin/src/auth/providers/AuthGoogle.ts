import { AuthKeycloak, getKeycloakRef } from './AuthKeycloak';

/** Google via Keycloak IdP — same token/UserInfo flow, different login hint. */
export class AuthGoogle extends AuthKeycloak {
  readonly authType = 'google' as const;

  login(): void {
    const kc = getKeycloakRef();
    if (kc) {
      kc.login({ idpHint: 'google' });
    }
  }
}

export const authGoogle = new AuthGoogle();
