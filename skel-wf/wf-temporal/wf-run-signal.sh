#!/bin/bash
# Signal workflow with data
# Usage: ./wf-signal.sh <run_id> <activity> <data_json>
# Example: ./wf-signal.sh abc123-def456 pol '{"ts":1234567890,"liabilities":[],"signature":"0xabc","signatureType":"public_key","publicKey":"0xdef"}'

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf}

RUN_ID=${1}
ACTIVITY=${2:-pol}
DATA=${3:-'{}'}

if [ -z "$RUN_ID" ]; then
  echo "Usage: $0 <run_id> [activity] [data_json]"
  echo "Example: $0 abc123-def456 pol '{\"ts\":1234567890,\"liabilities\":[],\"signature\":\"0xabc\",\"signatureType\":\"public_key\",\"publicKey\":\"0xdef\"}'"
  exit 1
fi

curl -X POST \
  -H 'Content-Type: application/json' \
  --data "{\"aid\": \"${ACTIVITY}\", \"data\": ${DATA}}" \
  ${SERVICE_URI}/run/${RUN_ID}/signal

echo
