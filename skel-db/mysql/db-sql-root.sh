#!/bin/bash
# --protocol=tcp is needed if connecting to localhost (127.0.0.1 works fine)
CWD=`echo $(dirname $(readlink -f $0))`

source db-env.sh

echo "DB_USER=$DB_USER"
echo "DB_PASS=$DB_PASS"
echo "DB_DATABASE=$DB_DATABASE"
echo "DB_HOST=$DB_HOST"

SQL_FILE=${1}

mysql --host ${DB_HOST} --port 3306 -u ${ROOT_USER} -p${ROOT_PASS} --protocol=tcp < ${SQL_FILE}
