#!/bin/bash
# Update a WorkflowSchema by id.  ./wf-schema-update.sh <id> '<json>'
ID=${1:-0}
BODY=${2:-'{"title":"updated"}'}
SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}
>&2 echo "$BODY"
curl -S -s -D /dev/stderr -X PUT --data "$BODY" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$SERVICE_URI/schema/${ID}"
