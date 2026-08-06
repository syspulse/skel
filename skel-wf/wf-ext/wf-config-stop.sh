#!/bin/bash
# Stop (Temporal terminate) a WorkflowConfig's running Engine workflow by its `id`.
# Hard stop (no cancellation handlers run); sets WorkflowConfig.status = TERMINATED.
#   ./wf-config-stop.sh <configId>
#   REASON='manual stop' ./wf-config-stop.sh <configId>    # optional reason forwarded to Temporal
ID=${1:?"Usage: wf-config-stop.sh <configId>  (REASON='...')"}
REASON=${REASON:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

URL="$SERVICE_URI/config/$ID/stop"
[[ -n "$REASON" ]] && URL="${URL}?reason=$(printf '%s' "$REASON" | jq -sRr @uri)"

curl -S -s -D /dev/stderr -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
