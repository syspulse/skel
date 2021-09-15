#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

export EKM_URI="http://localhost:30001"
export EKM_KEY="KEY-00000001"
export EKM_DEVICE="99072"

./run.sh $@
