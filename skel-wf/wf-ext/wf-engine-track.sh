#!/bin/bash
# Track WorkflowConfig(s) + all DetectorConfigs by polling wf-engine-resolve.sh periodically.
# /config/resolve returns the WorkflowConfig(s) with LIVE engine-mapped statuses (workflow status
# and per-DetectorConfig status), resolved by runtimeId (UUID) or workflowId; TYPE forces (rid|wid).
#
#   ./wf-engine-track.sh                                   # default demo id, auto-detect
#   ./wf-engine-track.sh <runtimeId|workflowId>[,<id>...]  # one or many ids in one call
#   TYPE=wid POLL=1000 ./wf-engine-track.sh PoR-DefaultProject-1783782976365
#
# The server must be running WITH an Engine (for live statuses):
#   ./run-wf.sh --engine=temporal://127.0.0.1:7233/default server
IDS=${1:-019f51c0-3917-731b-864d-3b9d326db0aa}
POLL=${POLL:-3000}

CWD=`echo $(dirname $(readlink -f $0))`

>&2 echo "Tracking '$IDS' every ${POLL}ms (Ctrl+C to stop)"
while true; do
  "$CWD/wf-engine-resolve.sh" "$IDS"   # reuses SERVICE_URI / ACCESS_TOKEN / TYPE from the environment
  echo
  sleep "$(awk "BEGIN{print $POLL/1000}")"
done
