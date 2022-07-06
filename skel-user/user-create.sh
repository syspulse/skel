#!/bin/bash

EMAIL=${1:-user-1@mail.com}
NAME=${2:-name-1}
EID=${3:-external-id}
USER_ID=${4:-00000000-0000-0000-1000-000000000001}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/user}

#DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"name\":\"$NAME\",\"eid\":\"$EID\"}"
if [ "$EMAIL" == "root" ]; then
   DATA_JSON="{ \"uid\":"ffffffff-0000-0000-9000-000000000001",\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"eid\":\"$EID\"}"
else
   DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"eid\":\"$EID\",\"uid\":\"$USER_ID\"}"
fi

2> echo $DATA_JSON
curl -s -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/
