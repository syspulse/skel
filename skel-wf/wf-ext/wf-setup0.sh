#!/bin/bash
# Bootstrap the default placement (tenant -> project -> contract) via the setup0 API.
# Lets a WorkflowConfig created with that contractId satisfy the external detector.contract_id FK.
# All fields are parameterized (env vars) and default to 0 / 'setup0' / 'DISABLED':
#   ./wf-setup0.sh
#   TENANT_ID=1 PROJECT_ID=1 CONTRACT_ID=1 NAME=myproj STATUS=ACTIVE ./wf-setup0.sh
TENANT_ID=${TENANT_ID:-0}
PROJECT_ID=${PROJECT_ID:-0}
CONTRACT_ID=${CONTRACT_ID:-0}
NAME=${NAME:-setup0}
STATUS=${STATUS:-DISABLED}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

URL="$SERVICE_URI/setup0?tenantId=${TENANT_ID}&projectId=${PROJECT_ID}&contractId=${CONTRACT_ID}&name=${NAME}&status=${STATUS}"
>&2 echo "URL: $URL"
curl -S -s -D /dev/stderr -X POST -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
