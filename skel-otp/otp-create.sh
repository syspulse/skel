#!/bin/bash

SECRET=${1}
USER=${2:-00000000-0000-0000-1000-000000000001}
NAME=${3:-name1}
ACCOUNT=${4:-account1}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/otp}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

#DATA_JSON=${2:-customer-0.json}
#curl -i -X POST --data "@$DATA_JSON=" -H 'Content-Type: application/json' $SERVICE_URI/

DATA_JSON="{\"secret\":\"$SECRET\",\"uid\":\"$USER\",\"name\":\"$NAME\",\"account\":\"$ACCOUNT\"}"

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/
