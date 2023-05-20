#!/bin/bash

EMAIL=${1:-user-1@mail.com}
NAME=${2:-name-1}
XID=${3:-external-id}

USER_ID=${USER_ID:-00000000-0000-0000-1000-000000000001}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}
AVATAR=${AVATAR:-http://s3.aws.com/avatar-1.jpg}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/user}

>&2 echo "USER_ID=$USER_ID"

#DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"name\":\"$NAME\",\"xid\":\"$XID\"}"
if [ "$USER_ID" == "random" ]; then
   DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"xid\":\"$XID\",\"avatar\":\"$AVATAR\"}"
else
   DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"xid\":\"$XID\",\"uid\":\"$USER_ID\",\"avatar\":\"$AVATAR\"}"
fi

2> echo $DATA_JSON
curl -S -s -D /dev/stderr -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/
