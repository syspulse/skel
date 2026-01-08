#!/bin/bash

TID=${1:-490}


SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/dash}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "DID=$DID"

curl -S -s -D /dev/stderr \
   -X GET \
   -H 'Content-Type: application/json' \
   -H "Authorization: Bearer $ACCESS_TOKEN" \
   $SERVICE_URI/${TID}

