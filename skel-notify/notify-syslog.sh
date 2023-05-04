#!/bin/bash

MSG=${1:-msg.json}
SUBJ=${2:-System Notification}
SEVERITY=${3:-10}
SCOPE=${4:-sys.enroll}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/notify}

if [ "$MSG" == "msg.json" ]; then
    MSG=`cat $MSG | jq @json`
else
    MSG="\"$MSG\""
fi

DATA_JSON="{ \"severity\":$SEVERITY, \"scope\":\"$SCOPE\", \"subj\":\"$SUBJ\", \"msg\":$MSG }"

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/syslog
