#!/bin/bash

source influx-${SITE}.cred

BUCKET=${1:-npp-bucket}
INFLUX_URI=${2:-http://localhost:8086}
INFLUX_ORG_ID=${3:-$INFLUX_ORG_ID}
TOKEN_NAME="$BUCKET-token"

echo "bucket: $BUCKET"
echo "org: $INFLUX_ORG_ID"
echo "influx: $INFLUX_URI"
echo "admin-token: $INFLUX_ADMIN_TOKEN"

T0="1970-01-01T00:00:00Z"
NOW=`date --rfc-3339=seconds | sed 's/ /T/'`

# -L is needed for redirect
curl -i -L --request POST \
  "${INFLUX_URI}/api/v2/delete/?org=${INFLUX_ORG_ID}&bucket=${BUCKET}" \
  --header "Authorization: Token ${INFLUX_ADMIN_TOKEN}" \
  --header "Content-Type: application/json" \
  --data '{ "start": "'"${T0}"'","stop": "'"${NOW}"'" }'
