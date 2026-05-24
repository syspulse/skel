#!/bin/bash

RID=${1:-DetectorWallet}
DATA_ARG=${2:-alerts/Alert-DetectorWallet-1.json}

OID=${OID}
STYLE=${STYLE:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/explain}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null || echo ""`}

if [ -f "$DATA_ARG" ]; then
  DATA=$(jq -c '.' "$DATA_ARG")
else
  if echo "$DATA_ARG" | jq -e . >/dev/null 2>&1; then
    DATA=$(echo "$DATA_ARG" | jq -c '.')
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
   -X POST \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   $SERVICE_URI/${RID}/explain${QUERY_PARAM}
