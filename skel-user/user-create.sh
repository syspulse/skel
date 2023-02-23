#!/bin/bash

EMAIL=${1:-user-1@mail.com}
NAME=${2:-name-1}
XID=${3:-external-id}
USER_ID=${4:-00000000-0000-0000-1000-000000000001}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
AVATAR=${AVATAR:-http://s3.aws.com/avatar-1.jpg}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/user}

#DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"name\":\"$NAME\",\"xid\":\"$XID\"}"
if [ "$EMAIL" == "root" ]; then
   DATA_JSON="{ \"uid\":"ffffffff-0000-0000-9000-000000000001",\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"xid\":\"$XID\",\"avatar\":\"$AVATAR\"}"
else
   DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"xid\":\"$XID\",\"uid\":\"$USER_ID\",\"avatar\":\"$AVATAR\"}"
fi

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/
