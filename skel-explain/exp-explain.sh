#!/bin/bash

RID=${1:-DetectorWallet}
DATA=${2:-\{\}}

OID=${OID}
SCHEMA=${SCHEMA}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/explain}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "RID=$RID"
>&2 echo "OID=$OID"
>&2 echo "DATA=$DATA"
>&2 echo "SCHEMA=$SCHEMA"

if [ "$OID" != "" ]; then
  Q_OID="\"oid\": \"${OID}\","
fi

if [ "$SCHEMA" != "" ]; then
  Q_SCHEMA="\"schema\": ${SCHEMA},"
fi

read -r -d '' DATA_JSON << EOM
{
  ${Q_OID}
  ${Q_SCHEMA}
  "data": ${DATA}
}
EOM

>&2 echo "$DATA_JSON"

curl -S -s -D /dev/stderr \
   -X POST \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   $SERVICE_URI/${RID}
