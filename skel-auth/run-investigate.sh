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

export AUTH_CLIENT_ID=$TWITTER_AUTH_CLIENT_ID
export AUTH_CLIENT_SECRET=$TWITTER_AUTH_CLIENT_SECRET

exec ../run-app.sh $APP $MAIN "$@"
