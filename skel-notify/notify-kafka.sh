#!/bin/bash

SUBJ=${1:-Attention}
MSG=${2:-Notification}
TO=${3:-localhost:9092/topic.alarm}

TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/notify/kafka}

if [ "$TO" == "" ]; then
   DATA_JSON="{ \"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"data\":\"$DATA\"}"
else
   DATA_JSON="{ \"to\":\"$TO\", \"subj\":\"$SUBJ\",\"msg\":\"$MSG\",\"data\":\"$DATA\"}"
fi

2> echo $DATA_JSON
curl -s -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/