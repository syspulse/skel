#!/bin/bash

RID=${1}

OID=${OID:-490}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/explain}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "OID=$OID"
>&2 echo "RID=$RID"

curl -S -s -D /dev/stderr \
   -X DELETE \
   -H 'Content-Type: application/json' \
   -H "Authorization: Bearer $ACCESS_TOKEN" \
   $SERVICE_URI/${OID}/${RID}
