#!/bin/bash
# Production demo: track a WorkflowConfig using ONLY the REST API (no CLI).
#   1. assemble + link a WorkflowConfig to a Temporal id     POST /temporal/assembly/<id>
#   2. poll the WorkflowConfig with live engine-mapped status GET  /config/resolve/<id>
#
#   ./wf-track-demo.sh                                    # default demo id + DSL
#   ./wf-track-demo.sh <temporalId> '<pipeline>'
#   POLL=1000 TYPE=wid ./wf-track-demo.sh PoR-DefaultProject-1783782976365 '[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]'
#
# The server must be running WITH an Engine, e.g.:
#   ./run-wf.sh --engine=temporal://127.0.0.1:7233/default server
ID=${1:-019f51c0-3917-731b-864d-3b9d326db0aa}
PIPELINE=${2:-'[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]'}
TYPE=${TYPE:-}
NS=${NS:-}
POLL=${POLL:-3000}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}
AUTH="Authorization: Bearer $ACCESS_TOKEN"

# 1. assemble + link the WorkflowConfig to the Temporal id (runtimeId or workflowId)
ASM_URL="$SERVICE_URI/temporal/assembly/$ID"
[[ -n "$NS" ]] && ASM_URL="$ASM_URL?ns=$NS"
>&2 echo "assemble+link: POST $ASM_URL"
curl -S -s -X POST --data "{\"pipeline\":\"$PIPELINE\"}" -H 'Content-Type: application/json' -H "$AUTH" "$ASM_URL"
echo

# 2. poll the WorkflowConfig (+ DetectorConfigs) with live engine-mapped statuses, indefinitely
RES_URL="$SERVICE_URI/config/resolve/$ID"
[[ -n "$TYPE" ]] && RES_URL="$RES_URL?type=$TYPE"
>&2 echo "tracking: GET $RES_URL every ${POLL}ms (Ctrl+C to stop)"
while true; do
  curl -S -s -X GET -H 'Content-Type: application/json' -H "$AUTH" "$RES_URL"
  echo
  sleep "$(awk "BEGIN{print $POLL/1000}")"
done
