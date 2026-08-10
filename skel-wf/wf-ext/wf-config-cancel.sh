#!/bin/bash
# Cancel (Temporal request-cancel) a WorkflowConfig's running Engine workflow by its `id`.
# Graceful cancel (the workflow's cancellation handlers run); sets WorkflowConfig.status = CANCELED.
#   OID=490 ./wf-config-cancel.sh <configId>
#   REASON='manual cancel' OID=490 ./wf-config-cancel.sh <configId>
ID=${1:?"Usage: wf-config-cancel.sh <configId>  (REASON='...' OID=... PID=...)"}
REASON=${REASON:-}
OID=${OID:-}
PID=${PID:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

URL="$SERVICE_URI/config/$ID/cancel"
Q=""
[[ -n "$REASON" ]] && Q="${Q:+$Q&}reason=$(printf '%s' "$REASON" | jq -sRr @uri)"
[[ -n "$OID" ]] && Q="${Q:+$Q&}oid=${OID}"
[[ -n "$PID" ]] && Q="${Q:+$Q&}pid=${PID}"
[[ -n "$Q" ]] && URL="${URL}?${Q}"

curl -S -s -D /dev/stderr -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
