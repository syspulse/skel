#!/bin/bash
#
# Refresh ACCESS_TOKEN must correspond to the uid who created token in the first place !
# Or with Admin role

ID=${1}
REFRESH_TOKEN=${2}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/auth}

>&2 echo "ACCESS_TOKEN=$ACCESS_TOKEN"

curl -i -X PUT -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/${ID}/refresh/${REFRESH_TOKEN}
