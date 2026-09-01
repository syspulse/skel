#!/bin/bash
# Set up a venv (first run), install deps, and run the DEMO_WORKFLOW_QUEUE worker.
# Usage: ./run-worker.sh [dynamic|static|--check] [namespace]
#   default mode dynamic; both modes register WorkflowTypes Demo and Demo-Human
#   --check: print registered types and exit (does not connect to Temporal)
#   namespace: 2nd arg, else TEMPORAL_NAMESPACE/NAMESPACE env, else "default"
set -e
CWD=$(dirname "$(readlink -f "$0")")
cd "$CWD"

python3 -m venv .venv 2>/dev/null || true
# shellcheck disable=SC1091
source .venv/bin/activate
pip install -q -r requirements.txt

export TEMPORAL_TARGET=${TEMPORAL_TARGET:-localhost:7233}
export TEMPORAL_NAMESPACE=${TEMPORAL_NAMESPACE:-${NAMESPACE:-default}}
export TASK_QUEUE=${TASK_QUEUE:-DEMO_WORKFLOW_QUEUE}
export WF_EXT_URL=${WF_EXT_URL:-http://localhost:8080/api/v1/wf/ext}

echo "worker: mode=${1:-${MODE:-dynamic}} ns=${2:-$TEMPORAL_NAMESPACE} queue=$TASK_QUEUE temporal=$TEMPORAL_TARGET wf-ext=$WF_EXT_URL types=Demo,Demo-Human"
if [ "${1:-}" = "--check" ]; then
  exec python worker.py --check
fi
exec python worker.py "$@"
