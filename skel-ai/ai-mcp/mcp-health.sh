#!/bin/bash

MCP_URI=${MCP_URI:-http://127.0.0.1:8080}

>&2 echo "MCP_URI=$MCP_URI"

curl -S -s -D /dev/stderr \
   -X GET \
   -H 'Content-Type: application/json' \
   "${MCP_URI}/health"
