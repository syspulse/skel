#!/bin/bash

EMAIL=${1:-user-1@mail.com}
NAME=${2:-name-1}
XID=${3:-XID-0001}
AVATAR=${4:-http://avatar/a0001.jpg}
USER_ID=${5:-00000000-0000-0000-1000-000000000001}
TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/enroll}

#DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"name\":\"$NAME\",\"xid\":\"$XID\"}"
if [ "$EMAIL" == "root" ]; then
   DATA_JSON="{ \"uid\":"ffffffff-0000-0000-9000-000000000001",\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"xid\":\"$XID\"}"
else
   DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"xid\":\"$XID\",\"avatar\":\"$AVATAR\"}"
fi

2> echo $DATA_JSON
curl -s -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/
