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

ARGS="$@"

APP_HOME=${APP_HOME:-`pwd`}

JAR=`ls ${APP_HOME}/target/scala-2.13/*assembly*.jar`
CP="${APP_HOME}/conf/:$JAR"

CONFIG="application${SITE}.conf"

MEM=${MEM:-1G}
STACK=${STACK:-512M}

>&2 echo "=== Class Path ======================================="
>&2 echo "$CP"| sed "s/\:/\n/g"
>&2 echo "======================================================"
>&2 echo "APP: $APP"
>&2 echo "APP_HOME: $APP_HOME"
>&2 echo "MAIN: $MAIN"
>&2 echo "OPT: $OPT"
>&2 echo "ARGS: $ARGS"
>&2 echo "SITE: ${SITE}"
>&2 echo "CONFIG: ${CONFIG}"
>&2 echo "MEM: ${MEM}"
>&2 echo "STACK: ${STACK}"

>&2 echo $CP
>&2 pwd

# command:
exec $JAVA_HOME/bin/java -Xss${STACK} -Xms${MEM} -Dcolor -Dconfig.resource=$CONFIG -cp $CP $AGENT $OPT $MAIN $ARGS
