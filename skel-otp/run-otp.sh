#!/bin/bash                                                                                                                                                                                            
export CWD=`echo $(dirname $(readlink -f $0))`
#cd $CWD

t=$CWD
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

MAIN=io.syspulse.skel.otp.App

echo "app: $APP"
echo "site: $SITE"
echo "main: $MAIN"

exec ${CWD}/../run-app.sh $APP $MAIN "$@"
