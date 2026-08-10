#!/bin/bash
# Send a SIGNAL to a running WorkflowConfig's Engine (Temporal) workflow.
#   OID=490 ./wf-config-signal.sh <configId> [signalName] [payloadJson]
#     signalName : default CONTINUE
#     payloadJson: JSON body delivered to the workflow's signal handler (default {})
# Examples:
#   OID=490 ./wf-config-signal.sh 6
#   OID=490 ./wf-config-signal.sh 6 CONTINUE '{"ok":true}'
ID=${1:?"Usage: wf-config-signal.sh <configId> [signalName=CONTINUE] [payloadJson]  (OID=... PID=...)"}
NAME=${2:-CONTINUE}
PAYLOAD=${3:-'{}'}
OID=${OID:-}
PID=${PID:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

URL="$SERVICE_URI/config/$ID/signal?name=$NAME"
[[ -n "$OID" ]] && URL="${URL}&oid=${OID}"
[[ -n "$PID" ]] && URL="${URL}&pid=${PID}"

echo "signal '$NAME' -> WorkflowConfig $ID  payload=$PAYLOAD"
curl -S -s -D /dev/stderr -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d "$PAYLOAD" "$URL"
