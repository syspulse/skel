#!/bin/bash

#TO="${1:-http://GET:ASYNC@localhost:8300/{msg}}"
#TO="${1:-http://POST:ASYNC@12345677@localhost:8300}"
# this will take Auth token from Server --http.auth
TO="${1:-http://POST:ASYNC@@localhost:8300}"
SCOPE=${2:-sys.wf}
SUBJ=${3:-Workflow}
MSG=${4:-/api/v1/webhook}

SEVERITY=${SEVERITY:-20}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/notify}

DATA_JSON="{ \"to\":\"$TO\", \"severity\":$SEVERITY, \"scope\":\"$SCOPE\", \"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"data\":\"$DATA\"}"

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/
