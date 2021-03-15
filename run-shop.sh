#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

export SITE="shop"
exec ./run-app.sh skel-shop io.syspulse.skel.shop.App $@

