#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

export EKM_KEY="000000000000001"
export EKM_DEVICE="99003"

exec ./run-ekm-docker.sh $@
