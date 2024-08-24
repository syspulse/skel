#!/bin/bash

ID=${1:-counter.1}
DELTA=${2:-2}

ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/odometer}

DATA_JSON=""

if [ "$DELTA" != "" ]; then
   if [ "$DATA_JSON" != "" ]; then
      DATA_JSON="$DATA_JSON,"   
   fi
   DATA_JSON="$DATA_JSON\"delta\":$DELTA"
fi

#DATA_JSON="{\"email\":\"$EMAIL\",\"name\":\"$NAME\",\"avatar\":\"$AVATAR\"}"
DATA_JSON="{\"id\":\"$ID\",$DATA_JSON}"

>&2 echo $DATA_JSON

curl -S -s -D /dev/stderr -X PUT --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$SERVICE_URI/${ID}"
