#!/bin/bash

INPUT=${1:-./projects/Project-1/issues/}
OUTPUT=${2:-"output-{ID}.pdf"}
NAME=${3:-Report-1}
XID=${4:-PROJ-0001}

TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

SERVICE_URI=${SERVICE_URI:-http://localhost:8080/api/v1/report}

DATA_JSON="{\"input\":\"$INPUT\",\"name\":\"$NAME\",\"output\":\"$OUTPUT\",\"xid\":\"$XID\"}"

2> echo $DATA_JSON
curl -s -X POST --data "$DATA_JSON" -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/
