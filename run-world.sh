#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

export SITE="world"
exec ./run-app.sh skel-db-world io.syspulse.db.world.App $@