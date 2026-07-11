#!/bin/bash

ID=${1}
LAYOUT=${2:-{\"grid\":{\"rows\":2,\"cols\":2,\"items\":[]}}}
#TEXT=${@:2}

TID=${TID:-490}
OID=${OID:-100}

NAME=${NAME:-Dashboard-1}
ICON=${ICON:-https://example.com/icon.png}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/dash}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "LAYOUT=$LAYOUT"
>&2 echo "TID=$TID"
>&2 echo "OID=$OID"
>&2 echo "ICON=$ICON"

read -r -d '' DATA_JSON << EOM
{
  "name": "${NAME}",
  "layout": ${LAYOUT},
  "icon": "${ICON}"
}
EOM

>&2 echo "$DATA_JSON"

curl -S -s -D /dev/stderr \
   -X PUT \
   -H 'Content-Type: application/json' \
   -H "Authorization: Bearer $ACCESS_TOKEN" \
   --data "$DATA_JSON" \
   $SERVICE_URI/${TID}/${OID}/${ID}
