#!/bin/bash
# Create a "Demo" WorkflowSchema and start it via the wf-ext API. Starting:
#   - creates a WorkflowConfig from the schema,
#   - starts the Temporal workflow  Type=Demo  on  DEMO_WORKFLOW_QUEUE,
#   - attaches the new WorkflowConfig.id as the Temporal Memo "cid",
#   - stores the input JSON below into WorkflowConfig.meta.input.
# The Python worker (run-worker.sh) then runs DemoStart/DemoWork/DemoReport, each reading the config
# back from GET /config/{cid}.
set -e
CWD=$(dirname "$(readlink -f "$0")")

WF_EXT_URL=${WF_EXT_URL:-http://localhost:8080/api/v1/wf/ext}
TOKEN=${WF_EXT_TOKEN:-}
AUTH=(); [ -n "$TOKEN" ] && AUTH=(-H "Authorization: Bearer $TOKEN")

# 1) create the schema (its NAME must equal the worker's static WORKFLOW_NAME, default "Demo")
echo "creating WorkflowSchema 'Demo' ..."
SID=$(curl -sf "${AUTH[@]}" -H 'Content-Type: application/json' -X POST "$WF_EXT_URL/schema" \
  -d '{"name":"Demo","title":"Demo Flow","version":"1.0.0","description":"3-activity python demo"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
echo "  schema id = $SID"

# 2) start it on DEMO_WORKFLOW_QUEUE; wid becomes the WorkflowId (and the config title)
WID="Demo-$(date +%s)"
echo "starting schema $SID on DEMO_WORKFLOW_QUEUE (wid=$WID) ..."
curl -sf "${AUTH[@]}" -H 'Content-Type: application/json' \
  -X POST "$WF_EXT_URL/schema/$SID/start?tq=DEMO_WORKFLOW_QUEUE&wid=$WID" \
  -d '{"hello":"world","n":42}' | python3 -m json.tool
