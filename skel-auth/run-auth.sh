#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

t=`pwd`;
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

source auth-cred-${SITE}.sh

echo "GOOGLE_AUTH_CLIENT_ID=$GOOGLE_AUTH_CLIENT_ID"
echo "GOOGLE_AUTH_CLIENT_SECRET=$GOOGLE_AUTH_CLIENT_SECRET"
echo "TWITTER_AUTH_CLIENT_ID=$TWITTER_AUTH_CLIENT_ID"
echo "TWITTER_AUTH_CLIENT_SECRET=$TWITTER_AUTH_CLIENT_SECRET"

MAIN=io.syspulse.skel.auth.App

echo "app: $APP"
echo "site: $SITE"
echo "main: $MAIN"

exec ../run-app.sh $APP $MAIN "$@"
