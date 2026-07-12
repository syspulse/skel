#!/bin/bash
# Assemble a WorkflowConfig from a DSL pipeline, link it to an Engine runtime by xid,
# then poll the runtime periodically and render the topology + per-step statuses.
#
# Runs with sensible defaults (the PoR-Flow demo workflow) when no parameters are given:
#   ./wf-engine-track.sh
#   ./wf-engine-track.sh <runtimeId>
#   ./wf-engine-track.sh <runtimeId> '[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]'
#   POLL=1000 DATASTORE=dir://store ./wf-engine-track.sh

# defaults (current demo workflow xid + its Event-History activities as DSL)
DEF_RUNTIME_ID="019f51c0-3917-731b-864d-3b9d326db0aa"
DEF_PIPELINE="[ProofOfOwnership] -> [ProofOfReserve] -> [Report] -> [Commit]"

RUNTIME_ID="${1:-$DEF_RUNTIME_ID}"
[ $# -gt 0 ] && shift
PIPELINE="${*:-$DEF_PIPELINE}"

DATASTORE=${DATASTORE:-mem://}
WF=${WF:-temporal://}
POLL=${POLL:-3000}

exec ./run-wf.sh --datastore="$DATASTORE" --wf="$WF" --poll="$POLL" assembly-track "$RUNTIME_ID" "$PIPELINE"
