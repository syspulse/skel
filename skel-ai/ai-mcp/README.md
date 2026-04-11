# MCP

```
Client                        Server
  │                              │
  │── GET /mcp/sse ─────────────>│   Opens SSE stream, gets session ID
  │<── event: endpoint ──────────│   Server sends endpoint URL
  │                              │
  │── POST /mcp/message ────────>│   Client sends initialize
  │<── event: message (SSE) ─────│   Server replies over SSE
  │                              │
  │── POST /mcp/message ────────>│   tools/list
  │<── event: message (SSE) ─────│   [ echo, add ]
  │                              │
  │── POST /mcp/message ────────>│   tools/call echo
  │<── event: message (SSE) ─────│   "Echo: hello"
```

## Tests

```bash
# 1. Open SSE stream (keep this running in one terminal)
curl -N http://localhost:8080/mcp/sse

# 2. In another terminal – initialize
curl -X POST "http://localhost:8080/mcp/message?sessionId=<ID_FROM_SSE>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

# 3. List tools
curl -X POST "http://localhost:8080/mcp/message?sessionId=<ID>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

# 4. Call the echo tool
curl -X POST "http://localhost:8080/mcp/message?sessionId=<ID>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"message":"hello world"}}}'

# 5. Call the add tool
curl -X POST "http://localhost:8080/mcp/message?sessionId=<ID>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"add","arguments":{"a":3,"b":7}}}'

```

## Claude integration

1. Add it to Claude Code's config

Claude Code reads MCP servers from ~/.claude/claude_mcp_servers.json (global) or .claude/claude_mcp_servers.json (project-local). Add an entry using the url type for SSE-based servers:

```json
{
  "mcpServers": {
    "skel-mcp": {
      "type": "url",
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```

Alternatively, you can add it via the CLI:

```bash
claude mcp add --transport sse skel-mcp http://localhost:8080/mcp/sse
```

For project-local scope (checked into your repo):

```bash
claude mcp add --transport sse --scope project skel-mcp http://localhost:8080/mcp/sse
```

Verify Claude Code sees it:

```bash
claude mcp list
claude mcp get skel-mcp
```

### Use the tools in Claude Code
Once connected, Claude Code will automatically discover and use your echo and add tools. You can also invoke them explicitly in a Claude session:

```
> Use the echo tool to say "hello from akka"
> Add 42 and 58 using the add tool
```