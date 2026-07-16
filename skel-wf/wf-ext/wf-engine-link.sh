#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

# Create a WorkflowConfig from an existing Temporal workflow by LINKING to EXISTING detectors,
# then resolve it. Unlike wf-engine-assembly.sh, this NEVER creates DetectorConfig/DetectorSchema:
#   - each pipeline node is resolved to an existing DetectorConfig by name (latest version);
#   - a name that is not found fails the whole request;
#   - the WorkflowSchema references each found DetectorConfig's DetectorSchema.
#
#   1. link + bind   POST /temporal/link/<id>     (id = runtimeId (UUID) or workflowId)
#   2. resolve       GET  /config/resolve/<id>    (WorkflowConfig + DetectorConfigs, live-mapped statuses)
#
#   ./wf-engine-link.sh <id> ['<pipeline>']
#   ./wf-engine-link.sh 59ffd0ae-126e-4c70-aa52-b96585bfe1da
#   NS=default ./wf-engine-link.sh PoR-DefaultProject-1783782976365 '[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]'
#
# The server must be running WITH an Engine:
#   ./run-wf.sh --engine=temporal://127.0.0.1:7233/default server
ID=${1:-019f51c0-3917-731b-864d-3b9d326db0aa}
PIPELINE=${2:-'[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]'}
NS=${NS:-}
ENGINE=${ENGINE:-temporal}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

LINK_URL="$SERVICE_URI/${ENGINE}/link/$ID"
RESOLVE_URL="$SERVICE_URI/config/resolve/$ID"

>&2 echo "ID: $ID"
>&2 echo "ENGINE: $ENGINE"
>&2 echo "NS: $NS"
>&2 echo "SERVICE_URI: $SERVICE_URI"
>&2 echo "ACCESS_TOKEN: $ACCESS_TOKEN"
>&2 echo "LINK_URL: $LINK_URL"
>&2 echo "RESOLVE_URL: $RESOLVE_URL"
>&2 echo "PIPELINE: $PIPELINE"

# 1. link (reference existing DetectorConfigs by name) + bind the WorkflowConfig to the Temporal id

[[ -n "$NS" ]] && LINK_URL="$LINK_URL?ns=$NS"
>&2 echo "link: POST $LINK_URL"
curl -S -s -X POST \
 --data "{\"pipeline\":\"$PIPELINE\"}" \
 -H 'Content-Type: application/json' \
 -H "Authorization: Bearer ${ACCESS_TOKEN}" \
 "$LINK_URL"


echo -e "\n--------------------------------\n"

# 2. resolve the created WorkflowConfig (+ DetectorConfigs) with live engine-mapped statuses
>&2 echo "resolve:  GET $RESOLVE_URL"
curl -S -s -X GET \
 -H 'Content-Type: application/json' \
 -H "Authorization: Bearer ${ACCESS_TOKEN}" \
 "$RESOLVE_URL"
