#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

exec ./run-app.sh skel-kafka io.syspulse.skel.kafka.App $@