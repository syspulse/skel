#!/bin/bash
# Get DetectorConfig by id, or list with optional paging
#   ./detector-config-get.sh [id]
#   FROM=0 SIZE=10 ./detector-config-get.sh
ID=${1:-}
FROM=${FROM:-0}
SIZE=${SIZE:-5}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

if [[ -n "$ID" ]]; then
  URL="$SERVICE_URI/detector/config/$ID"
else
  URL="$SERVICE_URI/detector/config"
  if [[ -n "$FROM" || -n "$SIZE" ]]; then
    URL="${URL}?from=${FROM}&size=${SIZE}"
  fi
fi

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
