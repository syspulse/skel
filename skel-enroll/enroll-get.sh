#!/bin/bash

ID=${1}
SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/enroll}
TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

curl -i -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/${ID}
