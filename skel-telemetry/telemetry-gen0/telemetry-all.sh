#!/bin/bash

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/telemetry}
TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" "$SERVICE_URI/"
