#!/bin/bash                                                                                                                                                                                            
export CWD=`echo $(dirname $(readlink -f $0))`
#cd $CWD
export APP_HOME=$CWD

t=$CWD
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

MAIN=io.syspulse.skel.tag.App

>&2 echo "app: $APP"
>&2 echo "site: $SITE"
>&2 echo "main: $MAIN"

exec ${CWD}/../run-app.sh $APP $MAIN $@
