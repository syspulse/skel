#!/bin/bash
# Start the Demo Workflow (schema name "Demo") via wf-schema-start.sh:
#   - task queue  DEMO_WORKFLOW_QUEUE  (what demo/worker.py polls)
#   - a default JSON input with a few attributes ("type","work","n"); wf-ext stores it in meta.input
#     and the Python DemoWork activity reads it back.
#
#   ./wf-schema-start-demo.sh <schemaId> [work]     # work -> input.work (default "process")
#   INPUT='{"type":"x","work":"y"}' ./wf-schema-start-demo.sh <schemaId>   # fully custom input
#   WID=my-id ./wf-schema-start-demo.sh <schemaId>                         # override the Temporal WorkflowId
#
# The <schemaId> is the id of the "Demo" WorkflowSchema (see demo/create-and-start.sh or the UI).
CWD=$(dirname "$(readlink -f "$0")")

SCHEMA_ID=${1:?"Usage: wf-schema-start-demo.sh <schemaId> [work]"}
WORK=${2:-process}   # second optional param -> the input "work" attribute (default "process")

export TASK_QUEUE=${TASK_QUEUE:-DEMO_WORKFLOW_QUEUE}
export INPUT=${INPUT:-"{\"type\":\"demo\",\"work\":\"${WORK}\",\"n\":42}"}

exec "${CWD}/wf-schema-start.sh" "$SCHEMA_ID"
