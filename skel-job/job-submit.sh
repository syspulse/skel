#!/bin/bash

NAME=${1:-Job-1}
SCRIPT=${2:-file://test-5.py}
INPUTS=${3:-inputs.json}
CONF=${4:-}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/job}

INPUTS=`cat $INPUTS | jq -c .`

# Sevice supports parseing '1;2;3' into list, but it still must be encoded into JSON array !
DATA_JSON="{\"name\":\"$NAME\",\"src\":\"$SCRIPT\",\"inputs\": $INPUTS}"

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/
