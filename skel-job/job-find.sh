#!/bin/bash

STATE=${1}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/job}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

if [ "$STATE" != "" ]; then
   SUFFIX="?"
fi
if [ "$STATE" != "" ]; then
   SUFFIX=$SUFFIX"state=${STATE}&"
fi

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/${SUFFIX}
