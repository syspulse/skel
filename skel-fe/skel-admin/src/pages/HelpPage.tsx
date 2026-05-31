import React from 'react';

interface EndpointRowProps {
  method: string;
  path: string;
  desc: string;
}

function EndpointRow({ method, path, desc }: EndpointRowProps) {
  const methodColor: Record<string, string> = {
    GET: 'bg-green-100 text-green-700',
    POST: 'bg-blue-100 text-blue-700',
    PUT: 'bg-yellow-100 text-yellow-700',
    DELETE: 'bg-red-100 text-red-700',
  };
  return (
    <tr className="border-b border-border hover:bg-muted">
      <td className="py-2 px-3">
        <span
          className={`text-xs font-bold px-2 py-0.5 rounded font-mono ${
            methodColor[method] ?? 'bg-muted text-muted-foreground'
          }`}
        >
          {method}
        </span>
      </td>
      <td className="py-2 px-3 font-mono text-xs text-foreground">{path}</td>
      <td className="py-2 px-3 text-sm text-muted-foreground">{desc}</td>
    </tr>
  );
}

export function HelpPage() {
  return (
    <div className="max-w-3xl mx-auto px-6 py-8 space-y-8">
      <h1 className="text-xl font-semibold text-foreground">Help &amp; Documentation</h1>

      {/* Overview */}
      <section>
        <h2 className="text-base font-semibold text-foreground mb-2">Overview</h2>
        <p className="text-sm text-muted-foreground leading-relaxed">
          The <strong>Explain Admin</strong> UI manages <em>Explain Rules</em> — named
          scripts that describe how to interpret or transform data. Each rule has a
          unique <code className="bg-muted px-1 rounded text-xs">rid</code> (rule
          ID), optional <code className="bg-muted px-1 rounded text-xs">oid</code>{' '}
          (owner/org ID), one or more scripts, and optional metadata.
        </p>
      </section>

      {/* API Endpoints */}
      <section>
        <h2 className="text-base font-semibold text-foreground mb-3">API Endpoints</h2>
        <div className="border border-border rounded overflow-hidden">
          <table className="min-w-full text-sm">
            <thead className="bg-nav text-nav-fg text-xs uppercase">
              <tr>
                <th className="py-2 px-3 text-left w-20">Method</th>
                <th className="py-2 px-3 text-left">Path</th>
                <th className="py-2 px-3 text-left">Description</th>
              </tr>
            </thead>
            <tbody className="bg-card">
              <EndpointRow
                method="GET"
                path="/api/v1/explain"
                desc="List all rules. Optional: ?oid=&rid="
              />
              <EndpointRow
                method="GET"
                path="/api/v1/explain/:rid"
                desc="Get rule by RID. Optional: ?oid="
              />
              <EndpointRow
                method="POST"
                path="/api/v1/explain/:rid"
                desc="Create a new rule with the given RID"
              />
              <EndpointRow
                method="PUT"
                path="/api/v1/explain/:rid"
                desc="Update an existing rule by RID"
              />
              <EndpointRow
                method="DELETE"
                path="/api/v1/explain/:rid"
                desc="Delete rule by RID. Optional: ?oid="
              />
              <EndpointRow
                method="DELETE"
                path="/api/v1/explain"
                desc="Delete all rules. Optional: ?oid="
              />
            </tbody>
          </table>
        </div>
      </section>

      {/* Script Types */}
      <section>
        <h2 className="text-base font-semibold text-foreground mb-3">Script Types</h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {[
            {
              typ: 'js',
              label: 'JavaScript',
              desc: 'Execute a JavaScript snippet. Has access to input data via context variables.',
            },
            {
              typ: 'ai',
              label: 'AI Prompt',
              desc: 'Send a prompt to an AI/LLM model. Returns the model response.',
            },
            {
              typ: 'jq',
              label: 'jq Query',
              desc: 'Apply a jq expression to JSON input data for filtering and transformation.',
            },
            {
              typ: 'regexp',
              label: 'Regular Expression',
              desc: 'Match and extract patterns from string input using a regex.',
            },
            {
              typ: 'str',
              label: 'String Template',
              desc: 'Simple string template with variable substitution.',
            },
          ].map((item) => (
            <div
              key={item.typ}
              className="border border-border rounded p-3 bg-card"
            >
              <div className="flex items-center gap-2 mb-1">
                <code className="text-xs bg-muted px-2 py-0.5 rounded font-mono font-bold">
                  {item.typ}
                </code>
                <span className="text-sm font-medium text-foreground">
                  {item.label}
                </span>
              </div>
              <p className="text-xs text-muted-foreground">{item.desc}</p>
            </div>
          ))}
        </div>
      </section>

      {/* Data Model */}
      <section>
        <h2 className="text-base font-semibold text-foreground mb-2">Data Model</h2>
        <pre className="bg-gray-900 text-green-400 text-xs rounded p-4 overflow-x-auto leading-relaxed">
{`// Rule
{
  "rid":     "my-rule",           // required, unique rule ID
  "oid":     "org-123",           // optional, owner/org ID
  "name":    "My Rule",           // optional, display name
  "desc":    "Description",       // optional
  "sid":     "session-id",        // optional
  "ts0":     1700000000000,       // creation timestamp (ms)
  "ts":      1700000000000,       // last update timestamp (ms)
  "scripts": [                    // one or more scripts
    {
      "typ":  "js",               // script type
      "src":  "return input * 2", // script source
      "opts": ""                  // optional options
    }
  ],
  "meta": {                       // optional key-value metadata
    "icon":  "🔍",                // special: icon for the table
    "tags":  ["prod", "v2"]
  }
}`}
        </pre>
      </section>

      {/* Usage Examples */}
      <section>
        <h2 className="text-base font-semibold text-foreground mb-2">Usage Examples</h2>
        <div className="space-y-3">
          <div>
            <div className="text-xs font-semibold text-muted-foreground uppercase mb-1">
              Create a rule (curl)
            </div>
            <pre className="bg-gray-900 text-green-400 text-xs rounded p-3 overflow-x-auto">
{`curl -X POST http://localhost:8080/api/v1/explain/my-rule \\
  -H 'Content-Type: application/json' \\
  -d '{
    "scripts": [{"typ": "str", "src": "Hello {{name}}"}],
    "name": "Greeting",
    "meta": {"icon": "👋"}
  }'`}
            </pre>
          </div>
          <div>
            <div className="text-xs font-semibold text-muted-foreground uppercase mb-1">
              List rules (curl)
            </div>
            <pre className="bg-gray-900 text-green-400 text-xs rounded p-3 overflow-x-auto">
{`curl http://localhost:8080/api/v1/explain
curl http://localhost:8080/api/v1/explain?oid=org-123`}
            </pre>
          </div>
          <div>
            <div className="text-xs font-semibold text-muted-foreground uppercase mb-1">
              Delete a rule (curl)
            </div>
            <pre className="bg-gray-900 text-green-400 text-xs rounded p-3 overflow-x-auto">
{`curl -X DELETE http://localhost:8080/api/v1/explain/my-rule`}
            </pre>
          </div>
        </div>
      </section>

      {/* Environment variables */}
      <section>
        <h2 className="text-base font-semibold text-foreground mb-2">
          Environment Variables
        </h2>
        <div className="border border-border rounded overflow-hidden">
          <table className="min-w-full text-sm">
            <thead className="bg-nav text-nav-fg text-xs uppercase">
              <tr>
                <th className="py-2 px-3 text-left">Variable</th>
                <th className="py-2 px-3 text-left">Default</th>
                <th className="py-2 px-3 text-left">Description</th>
              </tr>
            </thead>
            <tbody className="bg-card">
              {[
                {
                  name: 'VITE_API_URL',
                  def: 'http://localhost:8080/api/v1/explain',
                  desc: 'Backend API base URL',
                },
                {
                  name: 'VITE_AUTH_ENABLED',
                  def: 'true',
                  desc: 'Set to "false" to disable Keycloak (guest mode)',
                },
                {
                  name: 'VITE_KEYCLOAK_URL',
                  def: 'http://localhost:8180',
                  desc: 'Keycloak server URL',
                },
                {
                  name: 'VITE_KEYCLOAK_REALM',
                  def: 'master',
                  desc: 'Keycloak realm name',
                },
                {
                  name: 'VITE_KEYCLOAK_CLIENT_ID',
                  def: 'explain-admin',
                  desc: 'Keycloak client ID',
                },
              ].map((row) => (
                <tr key={row.name} className="border-b border-border hover:bg-muted">
                  <td className="py-2 px-3 font-mono text-xs text-foreground">
                    {row.name}
                  </td>
                  <td className="py-2 px-3 font-mono text-xs text-muted-foreground">
                    {row.def}
                  </td>
                  <td className="py-2 px-3 text-sm text-muted-foreground">{row.desc}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
