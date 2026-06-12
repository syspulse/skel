#!/bin/bash
# Delete a WorkflowConfig by id.  ./wf-config-del.sh <id>
ID=${1:-0}
SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}
curl -S -s -D /dev/stderr -X DELETE -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$SERVICE_URI/config/${ID}"
