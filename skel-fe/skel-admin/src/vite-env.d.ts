/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_EXPLAIN_API_URL: string;
  readonly VITE_DASH_API_URL: string;
  readonly VITE_WORKFLOW_API_URL: string;
  /** @deprecated use VITE_EXPLAIN_API_URL */
  readonly VITE_API_URL?: string;
  readonly VITE_AUTH_ENABLED: string;
  readonly VITE_KEYCLOAK_URL: string;
  readonly VITE_KEYCLOAK_REALM: string;
  readonly VITE_KEYCLOAK_CLIENT_ID: string;
  readonly VITE_SKEL_AUTH_URL?: string;
  readonly VITE_DEFAULT_LOGO_URL?: string;
  readonly VITE_GOOGLE_CLIENT_ID?: string;
  readonly VITE_APP_NAME?: string;
  readonly VITE_APP_LOGO?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
