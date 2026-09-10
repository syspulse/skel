#!/bin/bash
# Show Alerts index mapping and document count.
#   ./db-show.sh
CWD=`echo $(dirname $(readlink -f $0))`
cd "$CWD"
source db-env.sh

INDEX=${1:-$ES_INDEX}
HOST=${ES_HOST%/}

echo "ES_HOST=$HOST"
echo "ES_INDEX=$INDEX"

echo "--- mapping ---"
es_curl "$HOST/${INDEX}/_mapping"
echo
echo "--- count ---"
es_curl "$HOST/${INDEX}/_count"
echo
