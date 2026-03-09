#!/bin/bash

STATUS=${1:-}
WORKFLOW_TYPE=${2:-}
PAGE_SIZE=${3:-10}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf}

>&2 echo "STATUS=$STATUS"
>&2 echo "WORKFLOW_TYPE=$WORKFLOW_TYPE"
>&2 echo "PAGE_SIZE=$PAGE_SIZE"

# Build JSON with optional fields
DATA_JSON="{"
DATA_JSON="${DATA_JSON}\"pageSize\": ${PAGE_SIZE}"

if [ -n "$STATUS" ]; then
  DATA_JSON="${DATA_JSON}, \"status\": \"${STATUS}\""
fi

if [ -n "$WORKFLOW_TYPE" ]; then
  DATA_JSON="${DATA_JSON}, \"workflowType\": \"${WORKFLOW_TYPE}\""
fi

DATA_JSON="${DATA_JSON}}"

>&2 echo "$DATA_JSON"
>&2 echo "POST $SERVICE_URI/list"

curl -S -s -D /dev/stderr \
   -X POST \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   $SERVICE_URI/list
