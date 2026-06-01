#!/bin/bash

DELAY=${1:-2000}
SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/user}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/timeout/${DELAY}
