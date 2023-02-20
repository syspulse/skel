#!/bin/bash

ID=${1:-00000000-0000-0000-1000-000000000001}
SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/user}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/${ID}
