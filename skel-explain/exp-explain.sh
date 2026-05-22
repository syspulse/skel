#!/bin/bash

RID=${1:-DetectorWallet}
DATA_ARG=${2:-alerts/Alert-DetectorWallet-1.json}

OID=${OID}
STYLE=${STYLE:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/explain}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null || echo ""`}

# Build request `data`: single alert object, or {data:[...], total:N} for multiple alerts
build_data() {
  local json="$1"
  local count
  count=$(echo "$json" | jq -r 'if (.data | type) == "array" then (.data | length) else 0 end' 2>/dev/null)
  if [ "$count" -gt 1 ]; then
    echo "$json" | jq -c '{data: .data, total: (.total // (.data | length))}'
  elif [ "$count" -eq 1 ]; then
    echo "$json" | jq -c '.data[0]'
  else
    echo "$json" | jq -c '.'
  fi
}

if [ -f "$DATA_ARG" ]; then
  FILE_JSON=$(jq -c '.' "$DATA_ARG")
  DATA=$(build_data "$FILE_JSON")
  if [ -z "$OID" ]; then
    OID=$(echo "$FILE_JSON" | jq -r '.data[0].tenantId // empty' 2>/dev/null)
    [ "$OID" = "null" ] && OID=""
  fi
else
  # Inline JSON: alert file shape, multi-alert wrapper, or single alert object
  if echo "$DATA_ARG" | jq -e . >/dev/null 2>&1; then
    DATA=$(build_data "$DATA_ARG")
  else
    DATA="$DATA_ARG"
  fi
fi

>&2 echo "RID=$RID"
>&2 echo "OID=$OID"
>&2 echo "STYLE=$STYLE"
>&2 echo "DATA=$DATA"

read -r -d '' DATA_JSON << EOM
{
  "rid": "${RID}",
  "data": ${DATA}
}
EOM

>&2 echo "$DATA_JSON"

QUERY_PARAM=""

if [ -n "$OID" ]; then
  QUERY_PARAM="?oid=${OID}"
fi

if [ -n "$STYLE" ]; then
  if [ -n "$QUERY_PARAM" ]; then
    QUERY_PARAM="${QUERY_PARAM}&style=${STYLE}"
  else
    QUERY_PARAM="?style=${STYLE}"
  fi
fi

curl -S -s -D /dev/stderr \
   -X GET \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   $SERVICE_URI/${RID}/explain${QUERY_PARAM}
