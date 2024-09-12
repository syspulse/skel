import Keycloak from 'keycloak-js';

const clientUrl = process.env.REACT_APP_KEYCLOAK_URL || '';
const clientId = process.env.REACT_APP_KEYCLOAK_CLIENT_ID || '';
const clientRealm = process.env.REACT_APP_KEYCLOAK_CLIENT_REALM || '';

const keycloakConfig = {
  url: clientUrl,
  realm: clientRealm,
  clientId: clientId
};

const keycloak = new Keycloak(keycloakConfig);

console.log('Keycloak:', keycloakConfig, keycloak);

keycloak.init({ onLoad: 'check-sso' });


export const login = () => keycloak.login();
export const logout = () => keycloak.logout();
export const isLoggedIn = () => !!keycloak.token;
export const jwtToken = () => keycloak.token;

export default keycloak;