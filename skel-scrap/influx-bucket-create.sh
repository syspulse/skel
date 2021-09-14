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

curl --request POST \
	"$INFLUX_URI/api/v2/buckets" \
	--header "Authorization: Token ${INFLUX_ADMIN_TOKEN}" \
  --header "Content-type: application/json" \
  --data '{ "orgID": "'"${INFLUX_ORG_ID}"'", "name": "'"${BUCKET}"'", "retentionRules": [ { "type": "expire", "everySeconds": 86400, "shardGroupDurationSeconds": 0 } ] }'
