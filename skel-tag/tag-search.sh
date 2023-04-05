#!/bin/bash

TAGS=${1}
FROM=${2}
SIZE=${3}
CAT=${CAT:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/tag}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

if [ "$FROM" != "" ] || [ "$SIZE" != "" ] || [ "$CAT" != "" ];  then
   SUFFIX="?"
fi
if [ "$FROM" != "" ]; then
   SUFFIX=$SUFFIX"from=${FROM}&"
fi
if [ "$SIZE" != "" ]; then
   SUFFIX=$SUFFIX"size=${SIZE}"
fi
if [ "$CAT" != "" ]; then
   SUFFIX=$SUFFIX"cat=${CAT}"
fi

>&2 echo "$SUFFIX"

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/search/${TAGS}${SUFFIX}
