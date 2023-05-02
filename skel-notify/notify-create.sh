#!/bin/bash

TO=${1:-stdout://}
SUBJ=${2:-subject-1}
MSG=${3:-text-1}
USER=${4:-00000000-0000-0000-1000-000000000001}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/notify}

if [ "$TO" == "" ]; then
   DATA_JSON="{ \"to\":"stdout://",\"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"uid\":\"$USER\"}"
else
   DATA_JSON="{ \"to\":\"$TO\",\"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"uid\":\"$USER\"}"
fi

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/
