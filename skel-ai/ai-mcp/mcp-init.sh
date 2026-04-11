#!/bin/bash

SESSION_ID=${1:?session_id_required__see_mcp-sse.sh}

MCP_BASE=${MCP_BASE:-http://127.0.0.1:8080/api/v1/mcp}
JSONRPC_ID=${JSONRPC_ID:-1}

>&2 echo "MCP_BASE=$MCP_BASE"
>&2 echo "SESSION_ID=$SESSION_ID"
>&2 echo "JSONRPC_ID=$JSONRPC_ID"

read -r -d '' DATA_JSON << EOM
{"jsonrpc":"2.0","id":${JSONRPC_ID},"method":"initialize","params":{}}
EOM

>&2 echo "$DATA_JSON"

curl -S -s -D /dev/stderr \
   -X POST \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   "${MCP_BASE}/message?sessionId=${SESSION_ID}"
