#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

MAIN=io.syspulse.ekm.App

EKM_KEY=${EKM_KEY}
EKM_DEVICE=${EKM_DEVICE}

LOG_FILE="data-$EKM_DEVICE-{yyyy-MM-dd-HH-mm}.log"

cd ..
exec ./run-app.sh skel-telemetry $MAIN --ekm-key $EKM_KEY --ekm-device=$EKM_DEVICE --log-file ${LOG_FILE} $@
