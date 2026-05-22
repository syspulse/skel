#!/bin/bash

RID=${1}
OID=${OID-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/explain}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "RID=$RID"
>&2 echo "OID=$OID"

if [ -z "$RID" ]; then
  URL="$SERVICE_URI"
else
  URL="$SERVICE_URI/${RID}"
fi

if [ -n "$OID" ]; then
  URL="${URL}?oid=${OID}"
fi

curl -S -s -D /dev/stderr \
   -X GET \
   -H 'Content-Type: application/json' \
   -H "Authorization: Bearer $ACCESS_TOKEN" \
   "$URL"
