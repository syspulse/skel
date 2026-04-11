# MCP

Default **`server`** command exposes **AppServer**: MCP under `/api/v1/server/mcp` and JSON `GET`/`POST` on `/api/v1/server/{id}`. Use **`mcp`** command for MCP-only at `/api/v1/mcp` (legacy layout).

## `server` command (default)

```
Client                        Server
  │                              │
  │── GET …/server/mcp/sse ─────>│   Opens SSE stream, gets session ID
  │<── event: endpoint ──────────│   Server sends endpoint URL
  │                              │
  │── POST …/server/mcp/message ─>│   initialize / tools/list / tools/call
  │<── event: message (SSE) ─────│
  │                              │
  │── GET …/server/{id} ─────────>│   JSON: id + method GET
  │── POST …/server/{id} ────────>│   JSON: id + method POST + body
```

## `mcp` command

MCP only at **`/api/v1/mcp`** (no AppServer routes). Same JSON-RPC flow as above with paths `/api/v1/mcp/sse` and `/api/v1/mcp/message`.

## Tests (`server` default paths)

```bash
# 1. Open SSE stream (keep this running in one terminal)
curl -N http://localhost:8080/api/v1/server/mcp/sse

# 2. In another terminal – initialize
curl -X POST "http://localhost:8080/api/v1/server/mcp/message?sessionId=<ID_FROM_SSE>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

# 3. List tools
curl -X POST "http://localhost:8080/api/v1/server/mcp/message?sessionId=<ID>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

# 4. AppServer REST
curl http://localhost:8080/api/v1/server/my-id
curl -X POST http://localhost:8080/api/v1/server/my-id -d 'payload' -H 'Content-Type: text/plain'
```

## Claude integration (`server` SSE URL)

```json
{
  "mcpServers": {
    "skel-mcp": {
      "type": "url",
      "url": "http://localhost:8080/api/v1/server/mcp/sse"
    }
  }
}
```

```bash
claude mcp add --transport sse skel-mcp http://localhost:8080/api/v1/server/mcp/sse
claude mcp add --transport sse --scope project skel-mcp http://localhost:8080/api/v1/server/mcp/sse
```

### Use the tools in Claude Code

```
> Use the echo tool to say "hello from akka"
> Add 42 and 58 using the add tool
```
