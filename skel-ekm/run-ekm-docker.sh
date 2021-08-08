#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

DOCKER=syspulse/skel-ekm:latest

EKM_KEY=${EKM_KEY}
EKM_DEVICE=${EKM_DEVICE}

#INFLUX_URI="http://172.17.0.1:8086"
INFLUX_URI="http://192.168.1.245:8086"

LOG_FILE="/data/data-$EKM_DEVICE-{yyyy-MM-dd-HH-mm}.log"

docker run --rm --name ekm -p 8080:8080 -v `pwd`/../conf:/app/conf -v /mnt/share/data/ekm/data:/data $DOCKER --ekm-key $EKM_KEY --ekm-device=$EKM_DEVICE --influx-uri $INFLUX_URI --log-file ${LOG_FILE} $@
