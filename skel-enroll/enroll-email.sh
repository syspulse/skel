#!/bin/bash

EID=${1:-00000000-0000-0000-1000-000000000001}
EMAIL=${2:-user-1@mail.com}

TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/enroll}

DATA_JSON="{ \"id\":\"$EID\", \"data\": { \"email\":\"$EMAIL\",\"name\":\"$NAME\"}}"

2> echo $DATA_JSON
curl -s -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/$EID/email
