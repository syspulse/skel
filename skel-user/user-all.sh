#!/bin/bash

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/user}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

FROM=${FROM:-}
SIZE=${SIZE:-}

URL="$SERVICE_URI/"
if [[ -n "$FROM" || -n "$SIZE" ]]; then
  if [[ -z "$FROM" || -z "$SIZE" ]]; then
    echo "FROM and SIZE must both be set for paging (e.g. FROM=0 SIZE=10)" >&2
    exit 1
  fi
  URL="${URL}?from=${FROM}&size=${SIZE}"
fi

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
