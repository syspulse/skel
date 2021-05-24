#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

DOCKER=syspulse/skel-telemetry:latest

EKM_KEY="ODQwMzczOjE2NzMwMw"
EKM_DEVICE="11996"

INFLUX_URI="http://172.17.0.1:8086"

LOG_FILE="/data/data-$EKM_DEVICE-{yyyy-MM-dd-HH-mm}.log"

docker run --rm --name ekm -p 8080:8080 -v `pwd`/../conf:/app/conf -v /mnt/share/data/ekm:/data $DOCKER --ekm-key $EKM_KEY --ekm-device=$EKM_DEVICE --influx-uri $INFLUX_URI --log-file ${LOG_FILE} $@