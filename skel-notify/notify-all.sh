#!/bin/bash

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/notify}

curl -S -s -D /dev/stderr -X GET --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/
