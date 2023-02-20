#!/bin/bash

SEVERITY=${1:-10}
SCOPE=${2:-sys.enroll}
SUBJ=${3:-System Notification}
MSG=${4:-Enrollment=0000-200}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/notify/syslog}

DATA_JSON="{ \"severity\":$SEVERITY, \"scope\":\"$SCOPE\", \"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"data\":\"$DATA\"}"

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/
