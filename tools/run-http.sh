#!/bin/bash                                                                                                                                                                                            
export CWD=`echo $(dirname $(readlink -f $0))`

t=`pwd`;
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

export ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

MAIN=io.syspulse.skel.tools.HttpServer

>&2 echo "app: $APP"
>&2 echo "site: $SITE"
>&2 echo "main: $MAIN"
>&2 echo "ACCESS_TOKEN: $ACCESS_TOKEN"

exec $CWD/../run-app.sh $APP $MAIN "$@"
