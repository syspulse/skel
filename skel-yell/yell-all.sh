#!/bin/bash

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/yell}
TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

curl -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/