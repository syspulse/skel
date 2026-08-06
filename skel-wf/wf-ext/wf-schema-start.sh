#!/bin/bash
# Create a WorkflowConfig from a WorkflowSchema and start an Engine (Temporal) execution.
# WorkflowType == WorkflowSchema.name; WorkflowId == WID (if set) else new WorkflowConfig.title (or .name).
# The new config's xid is set to the RunId and the config is resolved.
#   ./wf-schema-start.sh <schemaId>
#   TASK_QUEUE=MY_QUEUE ./wf-schema-start.sh <schemaId>     # override the task queue
#   WID=my-workflow-id  ./wf-schema-start.sh <schemaId>     # override the Temporal WorkflowId
#   INPUT='{"k":"v"}'   ./wf-schema-start.sh <schemaId>     # caller JSON input (overrides the config payload)
ID=${1:?"Usage: wf-schema-start.sh <schemaId>  (TASK_QUEUE=.. WID=.. INPUT='{..}')"}
TASK_QUEUE=${TASK_QUEUE:-}
WID=${WID:-}
INPUT=${INPUT:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

echo "INPUT"
echo "$INPUT"
echo "-------"

URL="$SERVICE_URI/schema/$ID/start"
Q=""
[[ -n "$TASK_QUEUE" ]] && Q="taskQueue=${TASK_QUEUE}"
[[ -n "$WID" ]] && Q="${Q:+$Q&}wid=${WID}"
[[ -n "$Q" ]] && URL="${URL}?${Q}"

if [[ -n "$INPUT" ]]; then
  curl -S -s -D /dev/stderr -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" -d "$INPUT" "$URL"
else
  curl -S -s -D /dev/stderr -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
fi
