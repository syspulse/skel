#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

t=`pwd`;
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

MAIN=io.syspulse.skel.spark.CsvConvert

echo "app: $APP"
echo "site: $SITE"
echo "main: $MAIN"

export JAVA_OPTS="${JAVA_OPTS:--Xmx8g -Xms8g}"

exec ../../run-app.sh $APP $MAIN $@
