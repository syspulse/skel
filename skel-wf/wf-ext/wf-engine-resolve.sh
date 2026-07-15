#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

# Resolve WorkflowConfig(s) + all DetectorConfigs (with LIVE engine-mapped statuses) by
# runtimeId (UUID) and/or workflowId via the /config/resolve REST API (single call).
#
#   ./wf-engine-resolve.sh <id>[,<id>...]
#   TYPE=rid ./wf-engine-resolve.sh 59ffd0ae-126e-4c70-aa52-b96585bfe1da
#   TYPE=wid ./wf-engine-resolve.sh PoR-DefaultProject-1783782976365
#
# The server must be running WITH an Engine (for live statuses):
#   ./run-wf.sh --engine=temporal://127.0.0.1:7233/default server
IDS=${1:-019f51c0-3917-731b-864d-3b9d326db0aa}
TYPE=${TYPE:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

URL="$SERVICE_URI/config/resolve/$IDS"
[[ -n "$TYPE" ]] && URL="$URL?type=$TYPE"

>&2 echo "RUN_ID: $RUNTIME_ID"
>&2 echo "NS: $NS"
>&2 echo "ENGINE: $ENGINE"
>&2 echo "TYPE: $TYPE"

>&2 echo "URL: $URL"


curl -S -s -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
