#!/bin/bash

ID=${1:-'00000000-0000-0000-1000-000000000001'}
# EMAIL=${2:-email-2@server.org}
# NAME=${3:-NAME-new}
# AVATAR=${4:-https://avatars/avatar-new.jpg}

EMAIL=${2}
NAME=${3}
AVATAR=${4}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

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

>&2 echo $DATA_JSON

curl -S -s -D /dev/stderr -X PUT --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$SERVICE_URI/${ID}"
