#!/bin/bash

# Open MCP SSE stream (run in one terminal). First event is type "endpoint" with
# sessionId=... in the data — use that value as SESSION_ID for other mcp-*.sh scripts.

MCP_URI=${MCP_URI:-http://127.0.0.1:8080}

>&2 echo "MCP_URI=$MCP_URI"
>&2 echo "Streaming /mcp/sse (Ctrl+C to stop). Copy sessionId from the endpoint event data."

curl -N -S -s -D /dev/stderr \
   -H 'Accept: text/event-stream' \
   "${MCP_URI}/mcp/sse"
