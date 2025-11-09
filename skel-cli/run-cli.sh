#!/bin/bash                                                                                                                                                                                            
export CWD=`echo $(dirname $(readlink -f $0))`
#cd $CWD

#t=`pwd`;
t=$CWD
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

MAIN=io.syspulse.skel.cli.AppCliExample

echo "app: $APP"
echo "site: $SITE"
echo "main: $MAIN"

exec ${CWD}/../run-app.sh $APP $MAIN $@
