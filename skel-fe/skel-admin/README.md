# exp-admin

React admin UI for the [skel-explain](../README.md) service — manage Explain rules (create, update, delete), run explanations, filter by oid/rid/time range.

## Quick start

```bash
cd exp-admin
cp .env.example .env      # edit as needed
npm install
npm run dev               # http://localhost:3000
```

## Build

```bash
npm run build             # output: dist/
npm run preview           # serve the production build locally
```

## Environment variables

Set in `.env` (Vite loads `.env` automatically; prefix all names with `VITE_`):

| Variable | Default | Description |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080/api/v1/explain` | Backend API base URL |
| `VITE_AUTH_ENABLED` | `false` | Set to `true` to enable Keycloak authentication |
| `VITE_KEYCLOAK_URL` | `http://localhost:8180` | Keycloak server URL |
| `VITE_KEYCLOAK_REALM` | `master` | Keycloak realm |
| `VITE_KEYCLOAK_CLIENT_ID` | `explain-admin` | Keycloak client ID |

### No-auth mode (default)

`VITE_AUTH_ENABLED=false` — Keycloak is never initialized. The app runs as a guest user with no token. All API requests are sent without an `Authorization` header.

Useful for local development against a backend running with `--permissions=user` or no auth.

### Keycloak mode

`VITE_AUTH_ENABLED=true` — uses `keycloak-js` with `onLoad: 'login-required'`. The user is redirected to Keycloak on load. The token is refreshed every 60 seconds.

Keycloak must be configured with:
- A realm matching `VITE_KEYCLOAK_REALM`
- A public client matching `VITE_KEYCLOAK_CLIENT_ID`
- Valid redirect URIs including the app origin (e.g. `http://localhost:3000/*`)
- Google (or other) Identity Provider linked to the realm if social login is needed

### Runtime override

All `VITE_*` values can be overridden at runtime without a rebuild via the **Settings** page. Values are saved to `localStorage` and take precedence over the `.env` file. This is useful for pointing a deployed build at a different backend.

## Dev proxy

In dev mode (`npm run dev`) Vite proxies `/api/*` → `http://localhost:8080`. This means you can also use relative URLs (`/api/v1/explain`) in `VITE_API_URL` when running against the local backend without CORS issues.

## Features

### Explain page

- Table grid: icon (from `meta.icon` or default 📋), timestamp, oid, rid, name, desc
- **Filters** (frontend): oid substring, rid substring, time range
- **Time range**: Last 1h / 24h / 7 days / 30 days, or custom calendar start/end
- **UTC toggle**: timestamps shown in local time by default; click UTC to switch
- **Add** button (shown when nothing is selected) → opens slide-in panel in create mode
- **Row click** → opens slide-in panel in edit mode
- **Checkboxes + Select All** for multi-select → **Delete Selected** button replaces Add

### Slide-in panel (right side)

**Edit mode** (existing rule):
- RID and OID shown read-only
- Name, Desc, SID editable
- Script editor per step: type selector (`js`, `ai`, `jq`, `regexp`, `str`), source textarea, opts input
- `+ Script` button to add a new step; `×` to remove
- Meta key-value editor (`+ Add` row, `×` per row, JSON-aware values)
- Buttons: **Update** · **Delete** · **Cancel**

**Create mode** (Add button):
- RID is editable (required)
- Same fields as edit mode
- Buttons: **Create** · **Cancel**

### Settings page

- API URL — editable, saved to localStorage
- Auth mode — shown from env (`VITE_AUTH_ENABLED`)
- Keycloak fields — editable when auth is enabled, saved to localStorage

### Help page

- API endpoint reference
- Script type reference (`js`, `ai`, `jq`, `regexp`, `str`)
- Data model example
- curl usage examples
- Environment variable table

## Running with the backend

```bash
# Terminal 1 — backend (no auth, in-memory store)
cd ..
./run-explain.sh --datastore=mem://

# Terminal 2 — frontend
cd exp-admin
npm run dev
```

Open http://localhost:3000. The dev proxy handles CORS automatically.

## Docker / production build

```bash
npm run build
# Serve dist/ with any static file server, e.g.:
npx serve dist
# Or copy dist/ into an nginx image and set VITE_API_URL at build time:
VITE_API_URL=https://api.example.com/api/v1/explain npm run build
```
