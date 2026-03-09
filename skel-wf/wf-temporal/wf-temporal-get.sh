#!/bin/bash

RUN_ID=${1}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf}

if [ -z "$RUN_ID" ]; then
  >&2 echo "Usage: $0 <run_id>"
  >&2 echo "Example: $0 abc123-def456-ghi789"
  exit 1
fi

>&2 echo "RUN_ID=$RUN_ID"
>&2 echo "GET $SERVICE_URI/run/$RUN_ID"

curl -S -s -D /dev/stderr \
   -X GET \
   -H 'Content-Type: application/json' \
   $SERVICE_URI/run/$RUN_ID
