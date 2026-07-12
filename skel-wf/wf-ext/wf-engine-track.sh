#!/bin/bash
# Track WorkflowConfig(s) + all DetectorConfigs via the resolve REST API, polling periodically.
# Resolves by runtimeId (UUID) or workflowId; TYPE forces the mode (rid|wid).
#
#   ./wf-engine-track.sh                                   # default demo id, auto-detect
#   ./wf-engine-track.sh <id>[,<id>...]                    # one or many ids in one call
#   TYPE=rid ./wf-engine-track.sh <runtimeId>              # force runtimeId (xid)
#   TYPE=wid POLL=1000 ./wf-engine-track.sh <workflowId>   # force workflowId (meta.wid)
#
# The config(s) must already be present in the server store (e.g. created via
# ./run-wf-engine-track.sh or ./run-wf-engine-link.sh).
IDS=${1:-019f51c0-3917-731b-864d-3b9d326db0aa}
TYPE=${TYPE:-}
POLL=${POLL:-3000}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

URL="$SERVICE_URI/config/resolve/$IDS"
[[ -n "$TYPE" ]] && URL="$URL?type=$TYPE"

>&2 echo "Tracking $URL every ${POLL}ms (Ctrl+C to stop)"
while true; do
  curl -S -s -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
  echo
  sleep "$(awk "BEGIN{print $POLL/1000}")"
done
