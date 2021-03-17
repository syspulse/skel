#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

SITE=${SITE:-}
if [ "$SITE" != "" ]; then
   #SITE="-"${SITE}
   SITE=${SITE}
fi

CMD=${1:-list}
URL=${URL:-http://localhost:8083}

>&2 echo "URL: $URL"
>&2 echo "SITE: ${SITE}"

case "$CMD" in
   "list") 
      curl ${URL}/api/v1/currency/
      ;;
   "load")
      curl -X POST ${URL}/api/v1/currency/load
      ;;
   "clean")
      curl -X DELETE ${URL}/api/v1/currency
      ;;
   "get")
      curl ${URL}/api/v1/currency/${2}
      ;;
   *)
      echo "Country: ${1}"
      curl ${URL}/api/v1/currency/${1}
esac