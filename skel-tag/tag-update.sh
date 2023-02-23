#!/bin/bash

ID=${1:-0x000000000000000000000001}
CAT=${2:-}
TAGS=${3:-tag4}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/tag}

DATA_JSON=""

if [ "$CAT" != "" ]; then
   DATA_JSON="$DATA_JSON,\"cat\":\"$CAT\""
fi

if [ "$TAGS" != "" ]; then
   DATA_JSON="$DATA_JSON,\"tags\":[\"$TAGS\"]"
fi


# Sevice supports parseing '1;2;3' into list, but it still must be encoded into JSON array !
DATA_JSON="{\"id\":\"$ID\" ${DATA_JSON}}"

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X PUT --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/${ID}
