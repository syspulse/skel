#!/bin/bash

ATTR=${1:-cat=dex}
FROM=${2}
SIZE=${3}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/tag}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

PREFIX="?${ATTR}"

if [ "$FROM" != "" ]; then
   PREFIX=$PREFIX"&from=${FROM}"
fi
if [ "$SIZE" != "" ]; then
   PREFIX=$PREFIX"&size=${SIZE}"
fi

>&2 echo "PREFIX=${PREFIX}"

curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/find${PREFIX}
