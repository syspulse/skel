#!/bin/bash

SESSION_ID=${1:?session_id_required__see_mcp-sse.sh}
# Message: env MESSAGE, or all args after session id, or default (avoid " in message)
MESSAGE=${MESSAGE:-${*:2}}
MESSAGE=${MESSAGE:-hello world}

MCP_BASE=${MCP_BASE:-http://127.0.0.1:8080/api/v1/server/mcp}
JSONRPC_ID=${JSONRPC_ID:-3}

>&2 echo "MCP_BASE=$MCP_BASE"
>&2 echo "SESSION_ID=$SESSION_ID"
>&2 echo "JSONRPC_ID=$JSONRPC_ID"
>&2 echo "MESSAGE=$MESSAGE"

read -r -d '' DATA_JSON << EOM
{"jsonrpc":"2.0","id":${JSONRPC_ID},"method":"tools/call","params":{"name":"echo","arguments":{"message":"${MESSAGE}"}}}
EOM

>&2 echo "$DATA_JSON"

curl -S -s -D /dev/stderr \
   -X POST \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   "${MCP_BASE}/message?sessionId=${SESSION_ID}"
