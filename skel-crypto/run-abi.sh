#!/bin/bash                                                                                                                                                                                            
#
echo "--------------------------------------------------------------------------"
echo "JCE BC Security Exception fix:"
echo "File: $JAVA_HOME/jre/lib/security/java.security"
echo " security.provider.10=org.bouncycastle.jce.provider.BouncyCastleProvider"
echo ""
echo "Copy bcprov-jdk15on-1.65.01.jar to $PWD/lib"
echo "--------------------------------------------------------------------------"

export CWD=`echo $(dirname $(readlink -f $0))`
#cd $CWD

t=$CWD
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

MAIN=io.syspulse.skel.crypto.tool.AppABI

echo "app: $APP"
echo "site: $SITE"
echo "main: $MAIN"

exec ${CWD}/../run-app.sh $APP $MAIN "$@"
