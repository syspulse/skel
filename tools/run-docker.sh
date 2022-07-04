#!/bin/bash                                                                                                                                                                                            
#CWD=`echo $(dirname $(readlink -f $0))`
#cd $CWD

t=`pwd`;
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

DOCKER="syspulse/${APP}:latest"

DATA_DIR=${DATA_DIR:-/mnt/share/data}

echo "APP: $APP"
echo "SITE: $SITE"
echo "DOCKER: $DOCKER"
echo "DATA_DIR: $DATA_DIR"
echo "ARGS: $@"

docker run --rm --name $APP -p 8080:8080 -v `pwd`/conf:/app/conf -v $DATA_DIR:/data $DOCKER $@
