#!/bin/bash                                                                                                                                                                                            
#CWD=`echo $(dirname $(readlink -f $0))`
#cd $CWD

t=`pwd`;
APP=`basename "$t"`
CONF=`echo $APP | awk -F"-" '{print $2}'`

export SITE=${SITE:-$CONF}

DOCKER="syspulse/${APP}:latest"

DATA_DIR=${DATA_DIR:-/mnt/share/data}

echo "APP: $APP"
echo "SITE: $SITE"
echo "DOCKER: $DOCKER"
echo "DATA_DIR: $DATA_DIR"
echo "ARGS: $@"
echo "OPT: $OPT"

# ATTENTION: /app/bin/skel-app overrides arguments !
#
# process_args () {
#   local no_more_snp_opts=0
#   while [ $# -gt 0 ]; do
#     case "$1" in
#              --) shift && no_more_snp_opts=1 && break ;;
#        -h|-help) usage; exit 1 ;;
#     -v|-verbose) verbose=1 && shift ;;
#       -d|-debug) debug=1 && shift ;;
#     -no-version-check) no_version_check=1 && shift ;;

docker run --rm --name $APP -p 8080:8080 -v `pwd`/conf:/app/conf -v $DATA_DIR:/data \
   -e JAVA_OPTS=$OPT \
   -e DATASTORE=$DATASTORE \
   -e DB_HOST=$DB_HOST \
   -e SMTP_HOST=$SMTP_HOST \
   -e SMTP_USER=$SMTP_USER \
   -e SMTP_PASS=$SMTP_PASS \
   -e SMTP_FROM=$SMTP_FROM \
   $DOCKER $@
