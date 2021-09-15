#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

EKM_KEY=${EKM_KEY}
EKM_DEVICE=${EKM_DEVICE}

#INFLUX_URI="http://172.17.0.1:8086"
INFLUX_URI="http://canopus.u132.net:8086"

LOG_FILE="/data/data-$EKM_DEVICE-{yyyy-MM-dd-HH-mm}.log"

exec ../../tools/run-docker.sh $@
