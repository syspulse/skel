#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

t=`pwd`;
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

source auth-cred-${SITE}.sh

MAIN=${MAIN:-io.syspulse.skel.auth.investigate.TwitterOAuth2}

echo "app: $APP"
echo "site: $SITE"
echo "main: $MAIN"

echo "AUTH_CLIENT_ID=$AUTH_CLIENT_ID"
echo "AUTH_CLIENT_SECRET=$AUTH_CLIENT_SECRET"

exec ../run-app.sh $APP $MAIN "$@"
