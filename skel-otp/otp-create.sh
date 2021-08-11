#!/bin/bash

SECRET=${1}
USER=${2:-11111111-1111-1111-1111-000000000001}
NAME=${3:-name1}
ACCOUNT=${4:-account1}
SERVICE_URI=${5:-http://127.0.0.1:8080/api/v1/otp}

#DATA_JSON=${2:-customer-0.json}
#curl -i -X POST --data "@$DATA_JSON=" -H 'Content-Type: application/json' $SERVICE_URI/

DATA_JSON="{\"secret\":\"$SECRET\",\"userId\":\"$USER\",\"name\":\"$NAME\",\"account\":\"$ACCOUNT\"}"

2> echo $DATA_JSON
curl -s -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/
