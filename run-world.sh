#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

export SITE="world"
exec ./run-app.sh skel-world io.syspulse.skel.world.App $@

