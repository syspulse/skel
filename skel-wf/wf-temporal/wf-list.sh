#!/bin/bash

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf}

>&2 echo "GET $SERVICE_URI/schema"

curl -S -s -D /dev/stderr \
   -X GET \
   -H 'Content-Type: application/json' \
   $SERVICE_URI/schema
