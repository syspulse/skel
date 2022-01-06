#!/bin/bash

source influx-${SITE}.cred

BUCKET=${1:-$INFLUX_BUCKET}
INFLUX_ORG=${2:-$INFLUX_ORG}
INFLUX_URI=${3:-http://localhost:8086}
TOKEN_NAME="$BUCKET-token"

echo "bucket: $BUCKET"
echo "org: $INFLUX_ORG"
echo "influx: $INFLUX_URI"
echo "admin-token: $INFLUX_ADMIN_TOKEN"

curl --request POST \
	"$INFLUX_URI/api/v2/buckets" \
	--header "Authorization: Token ${INFLUX_ADMIN_TOKEN}" \
  --header "Content-type: application/json" \
  --data '{ "orgID": "'"${INFLUX_ORG}"'", "name": "'"${BUCKET}"'", "retentionRules": [ { "type": "expire", "everySeconds": 86400, "shardGroupDurationSeconds": 0 } ] }'
