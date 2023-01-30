#!/bin/bash

ID=${1:-'00000000-0000-0000-1000-000000000001'}
EMAIL=${2:-email-2@server.org}
NAME=${3:-NAME}
AVATAR=${4}

TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/user}

DATA_JSON=""

if [ "$EMAIL" != "" ]; then
   if [ "$DATA_JSON" != "" ]; then
      DATA_JSON="$DATA_JSON,"   
   fi
   DATA_JSON="$DATA_JSON\"email\":\"$EMAIL\""
fi

if [ "$NAME" != "" ]; then
   if [ "$DATA_JSON" != "" ]; then
      DATA_JSON="$DATA_JSON,"   
   fi
   DATA_JSON="$DATA_JSON\"name\":\"$NAME\""
fi

if [ "$AVATAR" != "" ]; then
   if [ "$DATA_JSON" != "" ]; then
      DATA_JSON="$DATA_JSON,"   
   fi
   DATA_JSON="$DATA_JSON\"avatar\":\"$AVATAR\""
fi

#DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"avatar\":\"$AVATAR\"}"
DATA_JSON="{$DATA_JSON}"

2> echo $DATA_JSON

curl -s -X PUT --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" "$SERVICE_URI/${ID}"
