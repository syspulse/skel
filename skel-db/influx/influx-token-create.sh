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
	"$INFLUX_URI/api/v2/authorizations" \
	--header "Authorization: Token ${INFLUX_ADMIN_TOKEN}" \
  --header "Content-type: application/json" \
  --data '{ "status": "active","description":"'"${TOKEN_NAME}"'", "orgID": "'"${INFLUX_ORG}"'", "permissions": [ { "action": "read","resource": { "type": "authorizations" } }, { "action": "read", "resource": { "type": "buckets" }},{"action": "write","resource": {"type": "buckets", "name": "'"${BUCKET}"'"}}]}'
