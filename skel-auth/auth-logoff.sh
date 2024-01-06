#!/bin/bash

USER_ID=${1:-00000000-0000-0000-0000-000000000000}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/auth}

curl -S -s -D /dev/stderr -X POST -d '{}' -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/logoff/${USER_ID}
