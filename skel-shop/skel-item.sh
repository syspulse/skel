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
      curl ${URL}/api/v1/item/
      ;;
   "load")
      curl -X POST ${URL}/api/v1/item/load
      ;;
   "random")
      count=${2:-10}
      delay=${3:-100}
      curl -X POST "${URL}/api/v1/item/load/random?count=${count}&delay=${delay}"
      ;;
   "random-get")
      count=${2:-10}
      curl ${URL}/api/v1/item/load/random?count=${count}
      ;;
   "clean")
      curl -X DELETE ${URL}/api/v1/item
      ;;
   "get")
      curl ${URL}/api/v1/item/${2}
      ;;
   *)
      echo "id: ${1}"
      curl ${URL}/api/v1/item/${1}
esac
