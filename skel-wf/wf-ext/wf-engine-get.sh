#!/bin/bash
# Get Engine (Temporal) runtime workflow state via the REST API.
#   ./wf-engine-get.sh                         # all workflows, all namespaces
#   NS=default ./wf-engine-get.sh              # all workflows in a namespace
#   NS=default ./wf-engine-get.sh <runtimeId>  # single workflow (expanded) by runtimeId (Temporal RunId)
#
# The server must be started with an Engine, e.g.:
#   ./run-wf.sh --engine=temporal://127.0.0.1:7233/default server
RUNTIME_ID=${1:-}
ENGINE=${ENGINE:-temporal}
NS=${NS:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

URL="$SERVICE_URI/engine/$ENGINE"
[[ -n "$NS" ]]         && URL="$URL/$NS"
[[ -n "$RUNTIME_ID" ]] && URL="$URL/$RUNTIME_ID"

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
