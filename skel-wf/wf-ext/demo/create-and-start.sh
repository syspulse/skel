#!/bin/bash
# Create a demo WorkflowSchema and start it via the wf-ext API. Starting:
#   - creates a WorkflowConfig from the schema,
#   - starts the Temporal workflow on DEMO_WORKFLOW_QUEUE
#       Type=Demo        (default)  or  Type=Demo-Human  (`human`),
#   - attaches the new WorkflowConfig.id as the Temporal Memo "cid",
#   - stores the input JSON below into WorkflowConfig.meta.input.
# The Python worker (run-worker.sh) then runs DemoStart/DemoWork/[DemoHuman]/DemoReport.
#
# Usage:
#   ./create-and-start.sh           # WorkflowType Demo (3 activities, no human gate)
#   ./create-and-start.sh human     # WorkflowType Demo-Human (wires DemoHuman; waits for CONTINUE)
set -e
CWD=$(dirname "$(readlink -f "$0")")

WF_EXT_URL=${WF_EXT_URL:-http://localhost:8080/api/v1/wf/ext}
TOKEN=${WF_EXT_TOKEN:-}
AUTH=(); [ -n "$TOKEN" ] && AUTH=(-H "Authorization: Bearer $TOKEN")

KIND=${1:-demo}
case "$KIND" in
  human|Demo-Human|demo-human)
    NAME="Demo-Human"
    TITLE="Demo Human Flow"
    DESC="DemoStart -> DemoWork -> DemoHuman (CONTINUE) -> DemoReport"
    # schema/dsl: Detector names become DetectorSchema.name == activity names
    PIPELINE="Detector.DemoStart -> Detector.DemoWork -> Detector.DemoHuman -> Detector.DemoReport"
    ;;
  demo|Demo|"")
    NAME="Demo"
    TITLE="Demo Flow"
    DESC="3-activity python demo"
    PIPELINE=""
    ;;
  *)
    echo "Usage: $0 [demo|human]" >&2
    exit 1
    ;;
esac

echo "creating WorkflowSchema '$NAME' ..."
if [ -n "$PIPELINE" ]; then
  SID=$(curl -sf "${AUTH[@]}" -H 'Content-Type: application/json' -X POST "$WF_EXT_URL/schema/dsl" \
    -d "{\"pipeline\":\"$PIPELINE\",\"name\":\"$NAME\"}" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
else
  SID=$(curl -sf "${AUTH[@]}" -H 'Content-Type: application/json' -X POST "$WF_EXT_URL/schema" \
    -d "{\"name\":\"$NAME\",\"title\":\"$TITLE\",\"version\":\"1.0.0\",\"description\":\"$DESC\"}" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["id"])')
fi
echo "  schema id = $SID  (WorkflowType=$NAME)"

# start it on DEMO_WORKFLOW_QUEUE; wid becomes the WorkflowId (and the config title)
WID="${NAME}-$(date +%s)"
echo "starting schema $SID on DEMO_WORKFLOW_QUEUE (wid=$WID) ..."
curl -sf "${AUTH[@]}" -H 'Content-Type: application/json' \
  -X POST "$WF_EXT_URL/schema/$SID/start?tq=DEMO_WORKFLOW_QUEUE&wid=$WID" \
  -d '{"hello":"world","n":42}' | python3 -m json.tool
