#!/bin/bash
# Create the Alerts index (detector-alert-search). Mapping is schema.json.
#   ./db-create.sh
#   ES_INDEX=detector-alert-search ./db-create.sh
CWD=`echo $(dirname $(readlink -f $0))`
cd "$CWD"
source db-env.sh

INDEX=${1:-$ES_INDEX}
SCHEMA=${2:-schema.json}
HOST=${ES_HOST%/}

echo "ES_HOST=$HOST"
echo "ES_INDEX=$INDEX"
echo "SCHEMA=$SCHEMA"

es_curl -X PUT "$HOST/${INDEX}" -H 'Content-Type: application/json' --data "@${SCHEMA}"
echo
