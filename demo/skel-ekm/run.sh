#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

t=`pwd`;
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

MAIN=io.syspulse.ekm.App

echo "app: $APP"
echo "site: $SITE"
echo "main: $MAIN"

EKM_KEY=${EKM_KEY}
EKM_DEVICE=${EKM_DEVICE}

LOG_FILE="data-$EKM_DEVICE-{yyyy-MM-dd-HH-mm}.log"

exec ../../run-app.sh $APP $MAIN --ekm.key=${EKM_KEY} --ekm.device=${EKM_DEVICE} --ekm.uri=${EKM_URI} --log.file ${LOG_FILE} $@