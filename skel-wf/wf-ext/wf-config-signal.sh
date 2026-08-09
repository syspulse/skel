#!/bin/bash
# Send a SIGNAL to a running WorkflowConfig's Engine (Temporal) workflow.
#   ./wf-config-signal.sh <configId> [signalName] [payloadJson]
#     signalName : default CONTINUE
#     payloadJson: JSON body delivered to the workflow's signal handler (default {})
# Examples:
#   ./wf-config-signal.sh 6                         # signal CONTINUE with {}
#   ./wf-config-signal.sh 6 CONTINUE '{"ok":true}'  # signal CONTINUE with a payload
ID=${1:?"Usage: wf-config-signal.sh <configId> [signalName=CONTINUE] [payloadJson]"}
NAME=${2:-CONTINUE}
PAYLOAD=${3:-'{}'}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

echo "signal '$NAME' -> WorkflowConfig $ID  payload=$PAYLOAD"
curl -S -s -D /dev/stderr -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d "$PAYLOAD" "$SERVICE_URI/config/$ID/signal?name=$NAME"
