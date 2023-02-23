#!/bin/bash

ID=${1}
FROM=${2}
SIZE=${3}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/tag}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

if [ "$FROM" != "" ] || [ "$SIZE" != "" ]; then
   PREFIX="?"
fi
if [ "$FROM" != "" ]; then
   PREFIX=$PREFIX"from=${FROM}"
fi
if [ "$SIZE" != "" ]; then
   PREFIX=$PREFIX"size=${SIZE}"
fi

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/${ID}$PREFIX
