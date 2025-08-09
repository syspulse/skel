#!/bin/bash

Q=${1:-"What model are you?"}
ID=${ID:-}
OID=${OID:-00000000-0000-0000-0000-000000000000}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/ai}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "Q=$Q"
>&2 echo "ID=$ID"

if [ "$ID" == "" ]; then
  ID=null
else
  ID="\"${ID}\""
fi

if [ "$DATA_JSON" == "" ]; then
  read -r -d '' DATA_JSON << EOM
  {
    "id": ${ID},
    "question": "${Q}",
    "model": "gpt-4o-mini"
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
   $SERVICE_URI/${OID}/prompt
