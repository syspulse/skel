#!/bin/bash

QUERY=${1:-""}
PAGE_SIZE=${2:-10}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf}

if [ -z "$QUERY" ]; then
  >&2 echo "Usage: $0 <query> [page_size]"
  >&2 echo "Example: $0 \"ExecutionStatus = 'Running'\" 20"
  >&2 echo "Example: $0 \"WorkflowType = 'PorWorkflow' AND ExecutionStatus = 'Running'\""
  exit 1
fi

>&2 echo "QUERY=$QUERY"
>&2 echo "PAGE_SIZE=$PAGE_SIZE"

read -r -d '' DATA_JSON << EOM
{
  "query": "${QUERY}",
  "pageSize": ${PAGE_SIZE}
}
EOM

>&2 echo "$DATA_JSON"
>&2 echo "POST $SERVICE_URI/query"

curl -S -s -D /dev/stderr \
   -X POST \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   $SERVICE_URI/query
