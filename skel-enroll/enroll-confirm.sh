#!/bin/bash

EID=${1:-00000000-0000-0000-1000-000000000001}
CODE=${2:-999980531569}

TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/enroll}

DATA_JSON="{ \"id\":\"$EID\", \"data\": { \"code\":\"$CODE\"}}"

2> echo $DATA_JSON
#curl -s -X GET --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/$EID/confirm/$CODE
curl -s -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/$EID/confirm/$CODE
