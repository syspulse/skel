#!/bin/bash
# Create a WorkflowConfig from a WorkflowSchema (sid), or assemble via DSL.
# Optional OID/PID (user JWT must match OID; admin may set any; JWT overrides oid for users):
#   OID=490 PID=p1 ./wf-config-create.sh <sid>
#   DSL='Detector.a -> Detector.b' ./wf-config-create.sh
SID=${1:-0}
DSL=${DSL:-}
WID=${WID:-}
WN=${WN:-}
OID=${OID:-}
PID=${PID:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

append_qp() {
  local url=$1
  local q=""
  [[ -n "$OID" ]] && q="${q:+$q&}oid=${OID}"
  [[ -n "$PID" ]] && q="${q:+$q&}pid=${PID}"
  if [[ -n "$q" ]]; then
    [[ "$url" == *\?* ]] && echo "${url}&${q}" || echo "${url}?${q}"
  else
    echo "$url"
  fi
}

if [[ -n "$DSL" ]]; then
  BODY="{\"pipeline\":\"$DSL\""
  [[ -n "$WID" ]] && BODY="$BODY,\"wid\":$WID"
  [[ -n "$WN" ]]  && BODY="$BODY,\"name\":\"$WN\""
  BODY="$BODY}"
  URL=$(append_qp "$SERVICE_URI/config/dsl")
else
  BODY="{\"sid\":$SID"
  [[ -n "$OID" ]] && BODY="$BODY,\"oid\":\"$OID\""
  [[ -n "$PID" ]] && BODY="$BODY,\"pid\":\"$PID\""
  BODY="$BODY}"
  URL=$(append_qp "$SERVICE_URI/config")
fi

>&2 echo "$BODY"
curl -S -s -D /dev/stderr -X POST --data "$BODY" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
