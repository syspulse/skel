#!/bin/bash

# Same service prefix as MCP (shared skel Server health under http.uri)
MCP_BASE=${MCP_BASE:-http://127.0.0.1:8080/api/v1/mcp}

>&2 echo "MCP_BASE=$MCP_BASE"

curl -S -s -D /dev/stderr \
   -X GET \
   -H 'Content-Type: application/json' \
   "${MCP_BASE}/health"
