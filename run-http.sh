#!/bin/bash                                                                                                                                                                                            
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

test -e server-cred.sh && source server-cred.sh


SITE=${:-SITE}
if [ "$SITE" != "" ]; then
   SITE="-"${SITE}
fi

JAR=`ls skel-http/target/scala-2.13/*assembly*.jar`
MAIN=io.syspulse.auth.App
CP="`pwd`/conf/:$JAR"

echo "=== Class Path ======================================="
echo "$CP"| sed "s/\:/\n/g"
echo "======================================================"
echo "MAIN: $MAIN"
echo "OPT: $OPT"
echo "ARGS: $@"
echo "Site: ${SITE}"

echo $CP

# command:
EXEC="$JAVA_HOME/bin/java -Xss512M -Dconfig.resource=application${SITE}.conf -cp $CP $AGENT $OPT $MAIN $@"
exec $EXEC
