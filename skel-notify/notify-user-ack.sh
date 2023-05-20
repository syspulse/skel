#!/bin/bash

ID=${1}
USER=${2:-00000000-0000-0000-1000-000000000001}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/notify}

DATA_JSON="{ \"id\":\"$ID\"}"

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X PUT --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/users/${USER}
