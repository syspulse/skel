#!/bin/bash
# Create PoR workflow schema "flow-1" with all steps: PoO -> PoR -> PoL -> Solvency -> Commit -> Report

NAME=${1:-flow-1}
TITLE=${TITLE:-"PoR Flow 1 (full)"}
DESC=${DESC:-"Proof of Reserves: PoO -> PoR -> PoL -> Solvency -> Commit -> Report"}
AUTHOR=${AUTHOR:-"admin"}
VERSION=${VERSION:-"1.0.0"}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf}

>&2 echo "NAME=$NAME"
>&2 echo "TITLE=$TITLE"
>&2 echo "DESC=$DESC"
>&2 echo "AUTHOR=$AUTHOR"
>&2 echo "VERSION=$VERSION"

read -r -d '' DATA_JSON << EOM
{
  "name": "${NAME}",
  "title": "${TITLE}",
  "description": "${DESC}",
  "author": "${AUTHOR}",
  "version": "${VERSION}",
  "tags": ["por", "flow-1", "client-1"],
  "nodes": [
    { "id": 1, "name": "Proof of Ownership", "aid": "poo", "typ": "detector" },
    { "id": 2, "name": "Proof of Reserves", "aid": "por", "typ": "detector" },
    { "id": 3, "name": "Proof of Liabilities", "aid": "pol", "typ": "detector" },
    { "id": 4, "name": "Solvency", "aid": "solvency", "typ": "detector" },
    { "id": 5, "name": "Commit", "aid": "commit", "typ": "detector" },
    { "id": 6, "name": "Report", "aid": "report", "typ": "detector" }
  ],
  "connections": [
    { "id": 1, "from": 1, "to": 2 },
    { "id": 2, "from": 2, "to": 3 },
    { "id": 3, "from": 3, "to": 4 },
    { "id": 4, "from": 4, "to": 5 },
    { "id": 5, "from": 5, "to": 6 }
  ]
}
EOM

>&2 echo "$DATA_JSON"
>&2 echo "POST $SERVICE_URI/schema"

curl -S -s -D /dev/stderr \
   -X POST \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   $SERVICE_URI/schema
