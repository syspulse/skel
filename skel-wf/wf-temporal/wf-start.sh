#!/bin/bash

# Usage: ./wf-start.sh <schema_id> [tid] [pid] [title] [workflow]
# Examples:
#   ./wf-start.sh 2 1 1 "Demo-Flow-{tid}-{pid}-{ts}"
#   ./wf-start.sh 3 1 1 "Demo-Flow-{tid}-{pid}-{ts}" "auto->human"

SCHEMA_ID=${1:-2}      # Default: Demo flow-1 (schemaId=2)
TID=${2:-1}            # Default tenant ID
PID=${3:-1}            # Default project ID
TITLE=${4:-"{name}-{tid}-{pid}-{ts}"}  # Default title with placeholders
WORKFLOW=${5:-}        # Optional custom workflow definition

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf}

>&2 echo "SCHEMA_ID=$SCHEMA_ID"
>&2 echo "TID=$TID"
>&2 echo "PID=$PID"
>&2 echo "TITLE=$TITLE"
>&2 echo "WORKFLOW=$WORKFLOW"

# Build JSON based on whether workflow is provided
if [ -z "$WORKFLOW" ]; then
  read -r -d '' DATA_JSON << EOM
{
  "id": ${SCHEMA_ID},
  "tid": ${TID},
  "pid": ${PID},
  "title": "${TITLE}"
}
EOM
else
  read -r -d '' DATA_JSON << EOM
{
  "id": ${SCHEMA_ID},
  "tid": ${TID},
  "pid": ${PID},
  "title": "${TITLE}",
  "workflow": "${WORKFLOW}"
}
EOM
fi

>&2 echo "$DATA_JSON"
>&2 echo "POST $SERVICE_URI/start"

curl -S -s -D /dev/stderr \
   -X POST \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   $SERVICE_URI/start
