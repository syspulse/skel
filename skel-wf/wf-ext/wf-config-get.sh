#!/bin/bash
# Get WorkflowConfig by id, or list with optional paging and ?entity=<csv> (graf,detector,schema | all; default graf)
# Optional OID/PID query params (user JWT must match OID; admin may omit or set any OID):
#   OID=490 ./wf-config-get.sh
#   OID=490 PID=p1 ./wf-config-get.sh 3
#   FROM=0 SIZE=10 ENTITY=detector,schema ./wf-config-get.sh
#   XID=<xid> ./wf-config-get.sh        # lookup by external id
#   Legacy path: OID alone without ID uses /config/oid/$OID when XID unset (also accepts PID)
ID=${1:-}
FROM=${FROM:-0}
SIZE=${SIZE:-5}
ENTITY=${ENTITY:-}
XID=${XID:-}
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

if [[ -n "$XID" ]]; then
  URL=$(append_qp "$SERVICE_URI/config/xid/$XID")
elif [[ -n "$OID" && -z "$ID" ]]; then
  # list by owner via dedicated path (PID still appended as query)
  URL=$(append_qp "$SERVICE_URI/config/oid/$OID")
elif [[ -n "$ID" ]]; then
  URL="$SERVICE_URI/config/$ID"
  [[ -n "$ENTITY" ]] && URL="${URL}?entity=${ENTITY}"
  URL=$(append_qp "$URL")
else
  URL="$SERVICE_URI/config"
  Q=""
  if [[ -n "$FROM" || -n "$SIZE" ]]; then
    Q="from=${FROM}&size=${SIZE}"
  fi
  [[ -n "$ENTITY" ]] && Q="${Q:+$Q&}entity=${ENTITY}"
  [[ -n "$Q" ]] && URL="${URL}?${Q}"
  URL=$(append_qp "$URL")
fi

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
