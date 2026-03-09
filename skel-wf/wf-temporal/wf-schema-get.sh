#!/bin/bash

WID=${1}

# if [ -z "$WID" ]; then
#   >&2 echo "Usage: $0 <workflow-id>"
#   exit 1
# fi

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf}

>&2 echo "WID=$WID"
>&2 echo "GET $SERVICE_URI/schema/$WID"

curl -S -s -D /dev/stderr \
   -X GET \
   -H 'Content-Type: application/json' \
   $SERVICE_URI/schema/${WID}
