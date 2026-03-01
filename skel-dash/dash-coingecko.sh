#!/bin/bash

ID=${1:-00000}
SRC=${2:-coingecko}

DID=${DID:-11111-11111-11111-11111}
TID=${TID:-490}
OID=${OID:-100}
LIMIT=${LIMIT:-100}

QUERY=${QUERY:-coins/ethereum/contract/0x7f39c581f595b53c5cb19bd0b3f8da6c935e2ca0/market_chart?days=7&vs_currency=usd}
#QUERY=${QUERY:-coins/ethereum/contract/0x7f39c581f595b53c5cb19bd0b3f8da6c935e2ca0/market_chart?days=7}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/dash}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "ID=$ID"
>&2 echo "SRC=$SRC"
>&2 echo "TID=$TID"
>&2 echo "OID=$OID"
>&2 echo "DID=$DID"
>&2 echo "QUERY=$QUERY"
>&2 echo "OPTS=$OPTS"
>&2 echo "LIMIT=$LIMIT"

if [ "$OPTS" != "" ]; then
  Q_OPTS="\"opts\": { $OPTS },"
fi


if [ "$DATA_JSON" == "" ]; then
  read -r -d '' DATA_JSON << EOM
  {
    "id": "${ID}",
    "src": "${SRC}",
    "query": "${QUERY}",
    ${Q_OPTS}
    "limit": ${LIMIT}
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
