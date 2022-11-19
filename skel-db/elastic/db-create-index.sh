#!/bin/bash

INDEX=${1:-index}
SCHEMA=${2:-schema.json}

curl -X PUT --data "@${SCHEMA}" -H 'Content-Type: application/json' http://localhost:9200/${INDEX}

