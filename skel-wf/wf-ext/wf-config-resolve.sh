#!/bin/bash
# Resolve a WorkflowConfig by its `id` against the Engine (live statuses: RUNNING / STARTING /
# RUNNING_FAILURE+meta.err / COMPLETED / UNRESOLVED ...). Uses /config/resolve with type=id, which
# matches the WorkflowConfig by numeric id and queries the Engine by that config's xid.
#   ./wf-config-resolve.sh <configId>[,<configId>...]
ID=${1:?"Usage: wf-config-resolve.sh <configId>[,<configId>...]"}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$SERVICE_URI/config/resolve/${ID}?type=id"
