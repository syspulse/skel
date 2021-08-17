#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD
export APP_HOME=`pwd`

t=`pwd`;
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

MAIN=io.syspulse.skel.ingest.IngestApp

$DEVICE=${2:-0001}
LOG_FILE="data-$DEVICE-{yyyy-MM-dd-HH-mm}.log"

echo "app: $APP"
echo "site: $SITE"
echo "main: $MAIN"

cd ..
exec ./run-app.sh $APP $MAIN --log-file ${LOG_FILE} $@
