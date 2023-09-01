#!/bin/bash

TO=${1:-00000000-0000-0000-1000-000000000001}
MSG=${2:-Attention!!!}
SUBJ=${3:-All User Notification}
SEVERITY=${4:-100}
SCOPE=${5:-sys.user}


ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/notify}

if [ "$TO" == "" ]; then
   DATA_JSON="{ \"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"severity\":$SEVERITY, \"scope\":\"$SCOPE\",\"data\":\"$DATA\"}"
else
   DATA_JSON="{ \"to\":\"$TO\", \"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"severity\":$SEVERITY, \"scope\":\"$SCOPE\",\"data\":\"$DATA\"}"
fi

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/user

