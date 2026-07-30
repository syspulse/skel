#!/bin/bash
# Get Workflow1Schema by id, or list with optional paging and ?entity=<csv> (graf,detector,schema | all; default graf)
#   ./wf-schema-get.sh [id]
#   FROM=0 SIZE=10 ENTITY=graf,schema ./wf-schema-get.sh
ID=${1:-}
FROM=${FROM:-0}
SIZE=${SIZE:-5}
ENTITY=${ENTITY:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

if [[ -n "$ID" ]]; then
  URL="$SERVICE_URI/schema/$ID"
  [[ -n "$ENTITY" ]] && URL="${URL}?entity=${ENTITY}"
else
  URL="$SERVICE_URI/schema"
  Q=""
  if [[ -n "$FROM" || -n "$SIZE" ]]; then
    Q="from=${FROM}&size=${SIZE}"
  fi
  [[ -n "$ENTITY" ]] && Q="${Q:+$Q&}entity=${ENTITY}"
  [[ -n "$Q" ]] && URL="${URL}?${Q}"
fi

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
