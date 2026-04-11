#!/bin/bash

# Open MCP SSE stream (run in one terminal). First event is type "endpoint" with
# sessionId=... in the data — use that value as SESSION_ID for other mcp-*.sh scripts.

# Service base (matches --http.uri / http.uri, default /api/v1/mcp)
MCP_BASE=${MCP_BASE:-http://127.0.0.1:8080/api/v1/server/mcp}

>&2 echo "MCP_BASE=$MCP_BASE"
>&2 echo "Streaming SSE (Ctrl+C to stop). Copy sessionId from the endpoint event data."

curl -N -S -s -D /dev/stderr \
   -H 'Accept: text/event-stream' \
   "${MCP_BASE}/sse"
