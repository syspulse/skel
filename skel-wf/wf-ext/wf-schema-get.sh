#!/bin/bash
# Get WorkflowSchema by id, or list with optional paging and ?detector=id|full
#   ./wf-schema-get.sh [id]
#   FROM=0 SIZE=10 DETECTOR=full ./wf-schema-get.sh
ID=${1:-}
FROM=${FROM:-}
SIZE=${SIZE:-}
DETECTOR=${DETECTOR:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

if [[ -n "$ID" ]]; then
  URL="$SERVICE_URI/schema/$ID"
  [[ -n "$DETECTOR" ]] && URL="${URL}?detector=${DETECTOR}"
else
  URL="$SERVICE_URI/schema"
  Q=""
  if [[ -n "$FROM" || -n "$SIZE" ]]; then
    if [[ -z "$FROM" || -z "$SIZE" ]]; then echo "FROM and SIZE must both be set" >&2; exit 1; fi
    Q="from=${FROM}&size=${SIZE}"
  fi
  [[ -n "$DETECTOR" ]] && Q="${Q:+$Q&}detector=${DETECTOR}"
  [[ -n "$Q" ]] && URL="${URL}?${Q}"
fi

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
