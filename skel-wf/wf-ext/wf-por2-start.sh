#!/bin/bash
# Start a PoR2 (wf-temporal) flow through the wf-ext API (wf-schema-start.sh).
#
# The Por2Worker's GenericWorkflow.execute(run: WorkflowRun) iterates run.steps, so the workflow
# input MUST be a WorkflowRun JSON with populated `steps` - otherwise the worker fails with:
#   NullPointerException: ... WorkflowRun.steps() is null
# (an empty /schema/{id}/start input is not the WorkflowConfig JSON and has no `steps`).
#
# This builds the correct WorkflowRun for the chosen flow (mirrors wf-temporal `por2-start` /
# Por2Schema: step id/name/typ + per-flow ordering) and passes it as INPUT to wf-schema-start.sh
# on the Por2Worker task queue (POR2_QUEUE).
#
# Usage:
#   ./wf-por2-start.sh <schemaId> [flow] [wid]
#     schemaId  : wf-ext WorkflowSchema id whose name == "PoR-Flow" (the Por2 workflow type)
#     flow      : flow-1 .. flow-6                (default: flow-1)
#     wid       : optional Temporal WorkflowId override
#   env: SERVICE_URI, ACCESS_TOKEN, TASK_QUEUE (default POR2_QUEUE)
#
#   flow-1: PoO -> PoR -> PoL(WAIT) -> Solvency -> Report -> Commit
#   flow-2: PoR -> PoL(WAIT) -> Solvency -> Report -> Commit
#   flow-3: PoR -> Report -> Commit
#   flow-4: PoO -> PoR -> Report -> Commit
#   flow-5: PoL only (WAIT)
#   flow-6: PoR -> Solvency(FAIL) -> Report   (Solvency throws -> workflow FAILs)
SID=${1:?"Usage: wf-por2-start.sh <schemaId> [flow] [wid]"}
FLOW=${2:-flow-1}
WID=${3:-${WID:-}}

CWD=$(dirname "$(readlink -f "$0")")
TASK_QUEUE=${TASK_QUEUE:-POR2_QUEUE}

# id -> DetectorConfig name / step type (mirrors Por2Schema.buildStepConfigs; 102=PoL is WAIT)
step_name() { case "$1" in
  100) echo "ProofOfOwnership";; 101) echo "ProofOfReserve";; 102) echo "ProofOfLiability";;
  103) echo "Solvency";;         104) echo "Report";;          105) echo "Commit";;
  106) echo "Solvency";;         *) echo "Step-$1";; esac; }
step_typ() { case "$1" in 102) echo "WAIT";; *) echo "AUTO";; esac; }

# flow -> ordered step ids (mirrors Por2Schema.getFlowSteps)
case "$FLOW" in
  flow-1) IDS="100 101 102 103 104 105";;
  flow-2) IDS="101 102 103 104 105";;
  flow-3) IDS="101 104 105";;
  flow-4) IDS="100 101 104 105";;
  flow-5) IDS="102";;
  flow-6) IDS="101 106 104";;
  *) echo "Unknown flow: '$FLOW' (valid: flow-1 .. flow-6)" >&2; exit 1;;
esac

# build the WorkflowRun.steps array
STEPS=""
for id in $IDS; do
  s="{\"id\":${id},\"name\":\"$(step_name "$id")\",\"typ\":\"$(step_typ "$id")\"}"
  STEPS="${STEPS:+$STEPS,}$s"
done

# WorkflowRun input (io.hacken.ext.wf.WorkflowRun): wid,rid,status,cursor,schema,steps,ns
INPUT="{\"wid\":\"${WID}\",\"rid\":null,\"status\":\"NEW\",\"cursor\":-1,\"schema\":${SID},\"steps\":[${STEPS}],\"ns\":null}"

echo "wf-por2-start: flow=${FLOW} schema=${SID} queue=${TASK_QUEUE} steps=[${IDS}]" >&2
echo "wf-por2-start: input=${INPUT}" >&2

INPUT="$INPUT" TASK_QUEUE="$TASK_QUEUE" WID="$WID" exec "$CWD/wf-schema-start.sh" "$SID"
