#!/bin/bash

SESSION_ID=${1:?session_id_required__see_mcp-sse.sh}

MCP_URI=${MCP_URI:-http://127.0.0.1:8080}
JSONRPC_ID=${JSONRPC_ID:-2}

>&2 echo "MCP_URI=$MCP_URI"
>&2 echo "SESSION_ID=$SESSION_ID"
>&2 echo "JSONRPC_ID=$JSONRPC_ID"

read -r -d '' DATA_JSON << EOM
{"jsonrpc":"2.0","id":${JSONRPC_ID},"method":"tools/list","params":{}}
EOM

>&2 echo "$DATA_JSON"

curl -S -s -D /dev/stderr \
   -X POST \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   "${MCP_URI}/mcp/message?sessionId=${SESSION_ID}"
