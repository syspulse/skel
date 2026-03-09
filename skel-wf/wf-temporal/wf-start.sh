#!/bin/bash

SRC=${1:-demo}
DATA=${2:-flow-1}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf}

>&2 echo "SRC=$SRC"
>&2 echo "DATA=$DATA"

read -r -d '' DATA_JSON << EOM
{
  "src": "${SRC}",
  "data": "${DATA}"
}
EOM

>&2 echo "$DATA_JSON"
>&2 echo "POST $SERVICE_URI/start"

curl -S -s -D /dev/stderr \
   -X POST \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   $SERVICE_URI/start
