#!/bin/bash
# Drop the Alerts index.
#   ./db-destroy.sh
CWD=`echo $(dirname $(readlink -f $0))`
cd "$CWD"
source db-env.sh

INDEX=${1:-$ES_INDEX}
HOST=${ES_HOST%/}

echo "ES_HOST=$HOST"
echo "ES_INDEX=$INDEX"

es_curl -X DELETE "$HOST/${INDEX}"
echo
