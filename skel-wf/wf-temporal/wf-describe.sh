#!/bin/bash

WORKFLOW_ID=${1}
RUN_ID=${2:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf}

if [ -z "$WORKFLOW_ID" ]; then
  >&2 echo "Usage: $0 <workflow_id> [run_id]"
  >&2 echo "Example: $0 por-workflow-Binance-1234567890"
  >&2 echo "Example: $0 por-workflow-Binance-1234567890 abc123-def456-ghi789"
  exit 1
fi

>&2 echo "WORKFLOW_ID=$WORKFLOW_ID"
>&2 echo "RUN_ID=$RUN_ID"

URL="$SERVICE_URI/describe/$WORKFLOW_ID"
if [ -n "$RUN_ID" ]; then
  URL="${URL}?runId=${RUN_ID}"
fi

>&2 echo "GET $URL"

curl -S -s -D /dev/stderr \
   -X GET \
   -H 'Content-Type: application/json' \
   "$URL"
