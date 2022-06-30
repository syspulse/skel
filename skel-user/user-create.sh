#!/bin/bash

EMAIL=${1:-user-1@mail.com}
NAME=${2:-name-1}
EID=${3:-external-id}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/user}

#DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"name\":\"$NAME\",\"eid\":\"$EID\"}"
DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"eid\":\"$EID\"}"

2> echo $DATA_JSON
curl -s -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/
