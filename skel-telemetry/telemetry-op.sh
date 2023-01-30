#!/bin/bash

ID=${1}
TS0=${2}
TS1=${3}
OP=${4}
SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/telemetry}
TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

curl -s -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" "$SERVICE_URI/${ID}?ts0=$TS0&ts1=$TS1&op=$OP"
