#!/bin/bash

NAME=${1:-WorkflowTest}
TITLE=${TITLE:-"Test Workflow"}
DESC=${DESC:-"Test workflow description"}
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
  "tags": ["test", "demo"],
  "nodes": [
    {
      "id": 1,
      "name": "Start Node",
      "aid": "node-start",
      "typ": "detector"
    },
    {
      "id": 2,
      "name": "End Node",
      "aid": "node-end",
      "typ": "detector"
    }
  ],
  "connections": [
    {
      "id": 1,
      "from": 1,
      "to": 2,      
    }
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
