import Keycloak from 'keycloak-js';

const clientUrl = process.env.REACT_APP_KEYCLOAK_URL || '';
const clientId = process.env.REACT_APP_KEYCLOAK_CLIENT_ID || '';
const clientRealm = process.env.REACT_APP_KEYCLOAK_CLIENT_REALM || '';

const keycloakConfig = {
  url: clientUrl,
  realm: clientRealm,
  clientId: clientId
};

var initialized = false;

const keycloak = new Keycloak(keycloakConfig);

console.log('Keycloak:', keycloakConfig, keycloak);

export const initKeycloak = async (onInit: (authenticated: boolean) => void) => {
  if (!initialized) {
    try {
      await keycloak.init({ onLoad: 'check-sso', checkLoginIframe: false });
      initialized = true;
      
      if (keycloak.token) {
        console.info('Login Success', keycloak.token);
        localStorage.setItem('jwtToken', keycloak.token);
        localStorage.setItem('refreshToken', keycloak.refreshToken || '');

        onInit(true);
      }

      console.log('Keycloak initialized');
    } catch (error) {
      console.error('Failed to initialize Keycloak', error);
    }
  }
};

export const login = async () => {
  try {
    await keycloak.login();
    if (keycloak.token) {
      console.info('Login Success', keycloak.token);
      localStorage.setItem('jwtToken', keycloak.token);
      localStorage.setItem('refreshToken', keycloak.refreshToken || '');
    }
  } catch (error) {
    console.error('Login failed', error);
  }
};

export const logout = () => {
  keycloak.logout();
  localStorage.setItem('jwtToken', '');
  localStorage.setItem('refreshToken', '');
}

export const isKeycloakLoggedIn = () => !!keycloak.token;
export const jwtToken = () => keycloak.token;

export default keycloak;