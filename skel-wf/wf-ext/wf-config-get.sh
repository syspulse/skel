#!/bin/bash
# Get WorkflowConfig by id, or list with optional paging and ?entity=<csv> (graf,detector,schema | all; default graf)
#   ./wf-config-get.sh [id]
#   FROM=0 SIZE=10 ENTITY=detector,schema ./wf-config-get.sh
#   XID=<xid> ./wf-config-get.sh        # lookup by external id
#   OID=<oid> ./wf-config-get.sh        # lookup by owner id
ID=${1:-}
FROM=${FROM:-0}
SIZE=${SIZE:-5}
ENTITY=${ENTITY:-}
XID=${XID:-}
OID=${OID:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

if [[ -n "$XID" ]]; then
  URL="$SERVICE_URI/config/xid/$XID"
elif [[ -n "$OID" ]]; then
  URL="$SERVICE_URI/config/oid/$OID"
elif [[ -n "$ID" ]]; then
  URL="$SERVICE_URI/config/$ID"
  [[ -n "$ENTITY" ]] && URL="${URL}?entity=${ENTITY}"
else
  URL="$SERVICE_URI/config"
  Q=""
  if [[ -n "$FROM" || -n "$SIZE" ]]; then
    Q="from=${FROM}&size=${SIZE}"
  fi
  [[ -n "$ENTITY" ]] && Q="${Q:+$Q&}entity=${ENTITY}"
  [[ -n "$Q" ]] && URL="${URL}?${Q}"
fi

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
