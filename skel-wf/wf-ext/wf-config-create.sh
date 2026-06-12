#!/bin/bash
# Create a WorkflowConfig from a WorkflowSchema (sid), or assemble via DSL.
#   ./wf-config-create.sh <sid>
#   DSL='Detector.a -> Detector.b' ./wf-config-create.sh
SID=${1:-0}
DSL=${DSL:-}
WID=${WID:-}
WN=${WN:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

if [[ -n "$DSL" ]]; then
  BODY="{\"pipeline\":\"$DSL\""
  [[ -n "$WID" ]] && BODY="$BODY,\"wid\":$WID"
  [[ -n "$WN" ]]  && BODY="$BODY,\"name\":\"$WN\""
  BODY="$BODY}"
  URL="$SERVICE_URI/config/dsl"
else
  BODY="{\"sid\":$SID}"
  URL="$SERVICE_URI/config"
fi

>&2 echo "$BODY"
curl -S -s -D /dev/stderr -X POST --data "$BODY" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
