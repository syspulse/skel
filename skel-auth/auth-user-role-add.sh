#!/bin/bash

USER=${1:-00000000-0000-0000-1000-000000000001}
ROLES=${2:-curator}
XID=${3:-101436214428674710353}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/auth}

DATA_JSON="{\"uid\":\"$USER\",\"roles\": [\"api\", \"$ROLES\"],\"xid\":\"$XID\" }"

curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/user/
