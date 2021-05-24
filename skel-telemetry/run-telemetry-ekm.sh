#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

EKM_KEY="ODQwMzczOjE2NzMwMw"
EKM_DEVICE="11996"

LOG_FILE="data-$EKM_DEVICE-{yyyy-MM-dd-HH-mm}.log"

cd ..
exec ./run-app.sh skel-telemetry io.syspulse.skel.telemetry.App --ekm-key $EKM_KEY --ekm-device=$EKM_DEVICE --log-file ${LOG_FILE} $@
