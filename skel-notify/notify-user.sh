#!/bin/bash

SEVERITY=${1:-100}
SCOPE=${2:-00000000-0000-0000-1000-000000000001}
SUBJ=${3:-All User Notification}
MSG=${4:-Attention!!!}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/notify/user}

DATA_JSON="{ \"severity\":$SEVERITY, \"scope\":\"$SCOPE\", \"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"data\":\"$DATA\"}"

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/
