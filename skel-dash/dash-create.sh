#!/bin/bash

LAYOUT=${1:-{\"grid\":{\"rows\":1,\"cols\":1,\"items\":[]}}}
#TEXT=${@:2}

TID=${TID:-490}
OID=${OID:-100}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/dash}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "LAYOUT=$LAYOUT"
>&2 echo "TID=$TID"
>&2 echo "OID=$OID"

read -r -d '' DATA_JSON << EOM
{
  "layout": ${LAYOUT},
  "name": "Dashboard 1",
  "desc": "Financial Information",
  "tags": ["system", "funds", "usd"]
}
EOM

>&2 echo "$DATA_JSON"

curl -S -s -D /dev/stderr \
   -X POST \
   -H 'Content-Type: application/json' \
   -H "Authorization: Bearer $ACCESS_TOKEN" \
   --data "$DATA_JSON" \
   $SERVICE_URI/${TID}/${OID}
