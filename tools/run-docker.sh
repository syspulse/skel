#!/bin/bash                                                                                                                                                                                            
#CWD=`echo $(dirname $(readlink -f $0))`
#cd $CWD

t=`pwd`;
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

DOCKER="syspulse/${APP}:latest"

echo "APP: $APP"
echo "SITE: $SITE"
echo "DOCKER: $DOCKER"
echo "ARGS: $@"

docker run --rm --name $APP -p 8080:8080 -v `pwd`/conf:/app/conf -v /mnt/share/data:/data $DOCKER $@