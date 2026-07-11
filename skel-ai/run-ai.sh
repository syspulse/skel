#!/bin/bash
export CWD=`echo $(dirname $(readlink -f $0))`
# cd $CWD

# bloop does not support stdin properly for interactive input
export APP_EXEC=${APP_EXEC:-sbt}

>&2 cat <<'EOF'

################################################################################
#                                                                              #
#   WARNING: only APP_EXEC=sbt is working with stdin (interactive input)       #
#            bloop does NOT support stdin properly — use APP_EXEC=sbt          #
#                                                                              #
################################################################################

EOF

# t=`pwd`;
t=$CWD
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

export ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

MAIN=io.syspulse.skel.ai.App

>&2 echo "app: $APP"
>&2 echo "site: $SITE"
>&2 echo "main: $MAIN"
>&2 echo "ACCESS_TOKEN: $ACCESS_TOKEN"
>&2 echo $@

exec $CWD/../run-app.sh $APP $MAIN $@
