#!/bin/bash

SUBJ=${1:-subject-1}
MSG=${2:-text-1}

TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/notify/stdout}

if [ "$TO" == "" ]; then
   DATA_JSON="{ \"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"data\":\"$DATA\"}"
else
   DATA_JSON="{ \"to\":\"$TO\", \"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"data\":\"$DATA\"}"
fi

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/
