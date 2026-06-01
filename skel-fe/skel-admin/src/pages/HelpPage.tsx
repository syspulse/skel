import React, { useState } from 'react';

type Tab = 'overview' | 'explain' | 'dash';

// ── Shared components ─────────────────────────────────────────────────────────

function EndpointRow({ method, path, desc }: { method: string; path: string; desc: string }) {
  const color: Record<string, string> = {
    GET:    'bg-green-100 text-green-700',
    POST:   'bg-blue-100 text-blue-700',
    PUT:    'bg-yellow-100 text-yellow-700',
    DELETE: 'bg-red-100 text-red-700',
  };
  return (
    <tr className="border-b border-border hover:bg-muted">
      <td className="py-2 px-3 w-20">
        <span className={`text-xs px-2 py-0.5 rounded font-mono ${color[method] ?? 'bg-muted text-muted-foreground'}`}>
          {method}
        </span>
      </td>
      <td className="py-2 px-3 font-mono text-xs text-foreground">{path}</td>
      <td className="py-2 px-3 text-xs text-muted-foreground">{desc}</td>
    </tr>
  );
}

function EndpointTable({ rows }: { rows: { method: string; path: string; desc: string }[] }) {
  return (
    <div className="border border-border rounded overflow-hidden">
      <table className="min-w-full">
        <thead className="bg-nav text-nav-fg text-xs uppercase">
          <tr>
            <th className="py-2 px-3 text-left w-20">Method</th>
            <th className="py-2 px-3 text-left">Path</th>
            <th className="py-2 px-3 text-left">Description</th>
          </tr>
        </thead>
        <tbody className="bg-card">
          {rows.map((r) => <EndpointRow key={r.method + r.path} {...r} />)}
        </tbody>
      </table>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-2">
      <h2 className="text-sm font-medium text-foreground">{title}</h2>
      {children}
    </section>
  );
}

function CodeBlock({ children }: { children: string }) {
  return (
    <pre className="bg-gray-900 text-green-400 text-xs rounded p-3 overflow-x-auto leading-relaxed">
      {children}
    </pre>
  );
}

// ── Overview tab ──────────────────────────────────────────────────────────────

function OverviewTab() {
  return (
    <div className="space-y-4">
      <div className="bg-card border border-border rounded shadow-sm p-4 space-y-4">
        <Section title="About">
          <p className="text-xs text-muted-foreground leading-relaxed">
            <strong className="text-foreground">skel-admin</strong> is a management UI for backend services.
            Use the <strong className="text-foreground">Explain</strong> module to manage named interpretation
            rules, and the <strong className="text-foreground">Dash</strong> module to manage dashboard layouts.
            Settings for API URLs, authentication, and appearance are in{' '}
            <strong className="text-foreground">Settings</strong>.
          </p>
        </Section>

        <Section title="Authentication">
          <p className="text-xs text-muted-foreground leading-relaxed">
            Authentication is handled via <strong className="text-foreground">Keycloak</strong>.
            Set <code className="bg-muted px-1 rounded">VITE_AUTH_ENABLED=false</code> to run in
            guest mode with no login required. In guest mode all API calls are made without a token.
          </p>
        </Section>
      </div>

      <div className="bg-card border border-border rounded shadow-sm p-4">
        <Section title="Environment Variables">
          <EndpointTable rows={[
            { method: 'GET', path: 'VITE_EXPLAIN_API_URL',           desc: 'Explain API base URL (default: http://localhost:8080/api/v1/explain)' },
            { method: 'GET', path: 'VITE_DASH_API_URL',      desc: 'Dash API base URL (default: http://localhost:8080/api/v1/dash)' },
            { method: 'GET', path: 'VITE_AUTH_ENABLED',      desc: 'Set to "false" to disable Keycloak and run in guest mode' },
            { method: 'GET', path: 'VITE_KEYCLOAK_URL',      desc: 'Keycloak server URL (default: http://localhost:8180)' },
            { method: 'GET', path: 'VITE_KEYCLOAK_REALM',    desc: 'Keycloak realm name (default: master)' },
            { method: 'GET', path: 'VITE_KEYCLOAK_CLIENT_ID',desc: 'Keycloak client ID (default: skel-admin)' },
          ]} />
        </Section>
      </div>
    </div>
  );
}

// ── Explain tab ───────────────────────────────────────────────────────────────

const EXPLAIN_ENDPOINTS = [
  { method: 'GET',    path: '/api/v1/explain',      desc: 'List all rules. Optional: ?oid= &rid=' },
  { method: 'GET',    path: '/api/v1/explain/:rid',  desc: 'Get rule by RID. Optional: ?oid=' },
  { method: 'POST',   path: '/api/v1/explain/:rid',  desc: 'Create a new rule with the given RID' },
  { method: 'PUT',    path: '/api/v1/explain/:rid',  desc: 'Update an existing rule by RID' },
  { method: 'DELETE', path: '/api/v1/explain/:rid',  desc: 'Delete rule by RID. Optional: ?oid=' },
  { method: 'DELETE', path: '/api/v1/explain',       desc: 'Delete all rules. Optional: ?oid=' },
];

const SCRIPT_TYPES = [
  { typ: 'js',     label: 'JavaScript',        desc: 'Execute a JavaScript snippet. Has access to input data via context variables.' },
  { typ: 'ai',     label: 'AI Prompt',          desc: 'Send a prompt to an AI/LLM model. Returns the model response as text.' },
  { typ: 'jq',     label: 'jq Query',           desc: 'Apply a jq expression to JSON input data for filtering and transformation.' },
  { typ: 'regexp', label: 'Regular Expression', desc: 'Match and extract patterns from string input using a regex.' },
  { typ: 'str',    label: 'String Template',    desc: 'Simple string template with variable substitution.' },
];

function ExplainTab() {
  return (
    <div className="space-y-4">
      <div className="bg-card border border-border rounded shadow-sm p-4">
        <Section title="API Endpoints">
          <EndpointTable rows={EXPLAIN_ENDPOINTS} />
        </Section>
      </div>

      <div className="bg-card border border-border rounded shadow-sm p-4">
        <Section title="Script Types">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {SCRIPT_TYPES.map((s) => (
              <div key={s.typ} className="border border-border rounded p-3 bg-muted">
                <div className="flex items-center gap-2 mb-1">
                  <code className="text-xs bg-card px-2 py-0.5 rounded font-mono border border-border">{s.typ}</code>
                  <span className="text-xs text-foreground">{s.label}</span>
                </div>
                <p className="text-xs text-muted-foreground">{s.desc}</p>
              </div>
            ))}
          </div>
        </Section>
      </div>

      <div className="bg-card border border-border rounded shadow-sm p-4">
        <Section title="Data Model">
          <CodeBlock>{`{
  "rid":     "my-rule",           // required, unique rule ID
  "oid":     "org-123",           // optional, owner/org ID
  "name":    "My Rule",           // optional, display name
  "desc":    "Description",       // optional
  "sid":     "session-id",        // optional
  "ts0":     1700000000000,       // creation timestamp (ms)
  "ts":      1700000000000,       // last update timestamp (ms)
  "scripts": [
    {
      "typ":  "js",               // script type
      "src":  "return input * 2", // script source
      "opts": ""                  // optional options
    }
  ],
  "meta": {                       // optional key-value metadata
    "icon":  "🔍",
    "width": 720
  }
}`}</CodeBlock>
        </Section>
      </div>

      <div className="bg-card border border-border rounded shadow-sm p-4 space-y-3">
        <Section title="Usage Examples">
          <div className="space-y-3">
            <div>
              <div className="text-xs text-muted-foreground mb-1">Create a rule</div>
              <CodeBlock>{`curl -X POST http://localhost:8080/api/v1/explain/my-rule \\
  -H 'Content-Type: application/json' \\
  -d '{
    "scripts": [{"typ": "str", "src": "Hello {{name}}"}],
    "name": "Greeting",
    "meta": {"icon": "👋"}
  }'`}</CodeBlock>
            </div>
            <div>
              <div className="text-xs text-muted-foreground mb-1">List rules</div>
              <CodeBlock>{`curl http://localhost:8080/api/v1/explain
curl http://localhost:8080/api/v1/explain?oid=org-123`}</CodeBlock>
            </div>
            <div>
              <div className="text-xs text-muted-foreground mb-1">Delete a rule</div>
              <CodeBlock>{`curl -X DELETE http://localhost:8080/api/v1/explain/my-rule`}</CodeBlock>
            </div>
          </div>
        </Section>
      </div>
    </div>
  );
}

// ── Dash tab ──────────────────────────────────────────────────────────────────

const DASH_ENDPOINTS = [
  { method: 'GET',    path: '/api/v1/dash',      desc: 'List all dashboard layouts' },
  { method: 'GET',    path: '/api/v1/dash/:id',  desc: 'Get dashboard by ID' },
  { method: 'POST',   path: '/api/v1/dash',      desc: 'Create a new dashboard layout' },
  { method: 'PUT',    path: '/api/v1/dash/:id',  desc: 'Update an existing dashboard by ID' },
  { method: 'DELETE', path: '/api/v1/dash/:id',  desc: 'Delete dashboard by ID' },
  { method: 'DELETE', path: '/api/v1/dash',      desc: 'Delete all dashboards' },
];

function DashTab() {
  return (
    <div className="space-y-4">
      <div className="bg-card border border-border rounded shadow-sm p-4">
        <Section title="API Endpoints">
          <EndpointTable rows={DASH_ENDPOINTS} />
        </Section>
      </div>

      <div className="bg-card border border-border rounded shadow-sm p-4">
        <Section title="Data Model">
          <CodeBlock>{`{
  "id":     "dash-abc123",        // auto-generated ID
  "name":   "My Dashboard",      // optional, display name
  "desc":   "Description",       // optional
  "tags":   ["prod", "team-a"],  // optional tags
  "ts0":    1700000000000,        // creation timestamp (ms)
  "ts":     1700000000000,        // last update timestamp (ms)
  "pid":    "parent-id",          // optional parent ID
  "tid":    "template-id",        // optional template ID
  "layout": {                     // arbitrary JSON layout definition
    "widgets": [],
    "cols": 12
  }
}`}</CodeBlock>
        </Section>
      </div>

      <div className="bg-card border border-border rounded shadow-sm p-4 space-y-3">
        <Section title="Usage Examples">
          <div className="space-y-3">
            <div>
              <div className="text-xs text-muted-foreground mb-1">Create a dashboard</div>
              <CodeBlock>{`curl -X POST http://localhost:8080/api/v1/dash \\
  -H 'Content-Type: application/json' \\
  -d '{
    "name": "My Dashboard",
    "tags": ["prod"],
    "layout": {"widgets": [], "cols": 12}
  }'`}</CodeBlock>
            </div>
            <div>
              <div className="text-xs text-muted-foreground mb-1">List dashboards</div>
              <CodeBlock>{`curl http://localhost:8080/api/v1/dash`}</CodeBlock>
            </div>
            <div>
              <div className="text-xs text-muted-foreground mb-1">Update a dashboard</div>
              <CodeBlock>{`curl -X PUT http://localhost:8080/api/v1/dash/dash-abc123 \\
  -H 'Content-Type: application/json' \\
  -d '{"name": "Renamed Dashboard"}'`}</CodeBlock>
            </div>
          </div>
        </Section>
      </div>
    </div>
  );
}

// ── HelpPage ──────────────────────────────────────────────────────────────────

export function HelpPage() {
  const [tab, setTab] = useState<Tab>('overview');

  const tabs: { id: Tab; label: string }[] = [
    { id: 'overview', label: 'Overview' },
    { id: 'explain',  label: 'Explain'  },
    { id: 'dash',     label: 'Dash'     },
  ];

  return (
    <div className="w-full px-4 py-3 space-y-2">
      <h1 className="text-lg text-foreground">Help &amp; Documentation</h1>

      <div className="flex border-b border-border gap-1">
        {tabs.map(({ id, label }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            className={`px-3 py-1 text-xs transition-colors border-b-2 -mb-px
              ${tab === id
                ? 'border-blue-500 text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="max-w-2xl">
        {tab === 'overview' && <OverviewTab />}
        {tab === 'explain'  && <ExplainTab />}
        {tab === 'dash'     && <DashTab />}
      </div>
    </div>
  );
}
