#!/bin/bash

ROLE=${1:-curator}
RESOURCE=${2:-data}
PERMISSIONS=${3:-write}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/auth}

DATA_JSON="{\"role\":\"$ROLE\",\"resources\": [{\"res\":\"$RESOURCE\",\"permissions\":[\"$PERMISSIONS\"]}]}"

curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/role/
