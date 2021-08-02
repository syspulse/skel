#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

DOCKER=syspulse/skel-http:latest

docker run --rm --name skel-http -p 8080:8080 -v `pwd`/../conf:/app/conf $DOCKER $@
