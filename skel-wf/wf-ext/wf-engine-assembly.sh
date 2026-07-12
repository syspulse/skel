#!/bin/bash
# Create a NEW WorkflowConfig from an existing Temporal workflow, then resolve it.
#   1. assembly + link  POST /temporal/assembly/<id>   (id = runtimeId (UUID) or workflowId)
#   2. resolve          GET  /config/resolve/<id>      (WorkflowConfig + DetectorConfigs, live-mapped statuses)
#
#   ./wf-engine-assembly.sh <id> ['<pipeline>']
#   ./wf-engine-assembly.sh 59ffd0ae-126e-4c70-aa52-b96585bfe1da
#   NS=default ./wf-engine-assembly.sh PoR-DefaultProject-1783782976365 '[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]'
#
# The server must be running WITH an Engine:
#   ./run-wf.sh --engine=temporal://127.0.0.1:7233/default server
ID=${1:-019f51c0-3917-731b-864d-3b9d326db0aa}
PIPELINE=${2:-'[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]'}
NS=${NS:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}
AUTH="Authorization: Bearer $ACCESS_TOKEN"

# 1. assembly + link the WorkflowConfig to the Temporal id
ASM_URL="$SERVICE_URI/temporal/assembly/$ID"
[[ -n "$NS" ]] && ASM_URL="$ASM_URL?ns=$NS"
>&2 echo "assembly: POST $ASM_URL"
curl -S -s -X POST --data "{\"pipeline\":\"$PIPELINE\"}" -H 'Content-Type: application/json' -H "$AUTH" "$ASM_URL"
echo

# 2. resolve the created WorkflowConfig (+ DetectorConfigs) with live engine-mapped statuses
>&2 echo "resolve:  GET $SERVICE_URI/config/resolve/$ID"
curl -S -s -X GET -H 'Content-Type: application/json' -H "$AUTH" "$SERVICE_URI/config/resolve/$ID"
echo
