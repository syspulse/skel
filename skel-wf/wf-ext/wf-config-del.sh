#!/bin/bash
# Delete a WorkflowConfig by id.
#   OID=490 PID=p1 ./wf-config-del.sh <id>
ID=${1:-0}
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

URL=$(append_qp "$SERVICE_URI/config/${ID}")
curl -S -s -D /dev/stderr -X DELETE -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
