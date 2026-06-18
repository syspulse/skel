#!/bin/bash

# Get user by id, or list users with optional paging.
#   ./user-get.sh [id]
#   ./user-get.sh '' 0 10
#   PAGE=0 SIZE=10 ./user-get.sh
# PAGE is 0-based (page 0 = first page); API uses from = PAGE * SIZE.

ID=${1:-}
PAGE=${PAGE:-${2:-0}}
SIZE=${SIZE:-${3:-}}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/user}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

if [[ -n "$ID" ]]; then
  URL="$SERVICE_URI/$ID"
else
  URL="$SERVICE_URI/"
  if [[ -n "$PAGE" || -n "$SIZE" ]]; then
    if [[ -z "$PAGE" || -z "$SIZE" ]]; then
      echo "PAGE and SIZE must both be set for paging (e.g. PAGE=0 SIZE=10)" >&2
      exit 1
    fi
    FROM=$((PAGE * SIZE))
    URL="${URL}?from=${FROM}&size=${SIZE}"
  fi
fi

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
