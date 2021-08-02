#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

exec ./run-app.sh skel-http io.syspulse.skel.service.App $@