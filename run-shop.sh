#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

export SITE="shop"
exec ./run-app.sh skel-world io.syspulse.skel.shop.App $@

