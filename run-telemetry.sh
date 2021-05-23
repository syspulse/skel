#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

EKM_HOST="http://localhost:3101"

exec ./run-app.sh skel-telemetry io.syspulse.skel.telemetry.App --ekm-host $EKM_HOST $@