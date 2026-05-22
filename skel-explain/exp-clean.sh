#!/bin/bash

# Delete all Explain rules for a given oid.
# OID=""  (default) — deletes all rules for default oid=""
# OID=490 — deletes all rules for oid 490

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/explain}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null || echo ""`}

OID=${OID-""}

>&2 echo "OID=$OID"

if [ -n "$OID" ]; then
  URL="${SERVICE_URI}?oid=${OID}"
else
  URL="${SERVICE_URI}"
fi

>&2 echo "DELETE $URL"

curl -S -s -D /dev/stderr \
  -X DELETE \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  "$URL"
