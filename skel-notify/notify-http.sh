#!/bin/bash

TO="${1:-http://localhost:8300/{msg}}"
SCOPE=${2:-sys.wf}
SUBJ=${3:-Workflow}
MSG=${4:-Pipeline-0001}

SEVERITY=${SEVERITY:-20}

TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/notify}

DATA_JSON="{ \"to\":\"$TO\", \"severity\":$SEVERITY, \"scope\":\"$SCOPE\", \"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"data\":\"$DATA\"}"

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/
