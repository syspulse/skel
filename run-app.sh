#!/bin/bash                                                                                                                                                                                            
#CWD=`echo $(dirname $(readlink -f $0))`
#cd $CWD

test -e server-cred.sh && source server-cred.sh


SITE=${SITE:-}
if [ "$SITE" != "" ]; then
   SITE="-"${SITE}
fi

APP=${1}
MAIN=${2}

shift
shift 

JAR=`ls ${APP}/target/scala-2.13/*assembly*.jar`
CP="`pwd`/conf/:$JAR"

CONFIG="application${SITE}.conf"

echo "=== Class Path ======================================="
echo "$CP"| sed "s/\:/\n/g"
echo "======================================================"
echo "APP: $APP"
echo "MAIN: $MAIN"
echo "OPT: $OPT"
echo "ARGS: $@"
echo "Site: ${SITE}"
echo "Config: ${CONFIG}"

echo $CP

# command:
EXEC="$JAVA_HOME/bin/java -Xss512M -Dconfig.resource=$CONFIG -cp $CP $AGENT $OPT $MAIN $@"
exec $EXEC
