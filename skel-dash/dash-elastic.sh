#!/bin/bash

ID=${1:-detector-alert-search,detector-event-search}
SRC=${2:-elastic}

DID=${DID:-11111-11111-11111-11111}
TID=${TID:-490}
OID=${OID:-100}
TYP=${TYP}

QUERY=${QUERY}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/dash}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "ID=$ID"
>&2 echo "SRC=$SRC"
>&2 echo "TID=$TID"
>&2 echo "OID=$OID"
>&2 echo "DID=$DID"
>&2 echo "QUERY=$QUERY"
>&2 echo "TYP=$TYP"

# Query
# {
#   "track_total_hits" : 99999999,
#   "query": {
#     "query_string": {
#       "query": "coid:4976 AND deid:11881 AND se:INFO AND ts:>=2025-03-13"
#     }
#   }
# }

if [ "$DATA_JSON" == "" ]; then
  read -r -d '' DATA_JSON << EOM
  {
    "id": "${ID}",
    "src": "${SRC}",
    "typ": "${TYP}",
    "query": ${QUERY},
    "limit": 100
  }
EOM

  #
fi

>&2 echo "$DATA_JSON"

curl -S -s -D /dev/stderr \
   -X POST \
   -H 'Content-Type: application/json' \
   -H "Authorization: Bearer $ACCESS_TOKEN" \
   --data "$DATA_JSON" \
   $SERVICE_URI/${TID}/${OID}/${DID}/data
