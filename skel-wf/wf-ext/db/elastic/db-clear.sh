#!/bin/bash
# Delete all documents in the Alerts index (keep mapping).
#   ./db-clear.sh
CWD=`echo $(dirname $(readlink -f $0))`
cd "$CWD"
source db-env.sh

INDEX=${1:-$ES_INDEX}
HOST=${ES_HOST%/}

echo "ES_HOST=$HOST"
echo "ES_INDEX=$INDEX"

es_curl -X POST "$HOST/${INDEX}/_delete_by_query?refresh=true" \
  -H 'Content-Type: application/json' \
  --data '{"query":{"match_all":{}}}'
echo
