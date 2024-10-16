#!/bin/bash
# --protocol=tcp is needed if connecting to localhost (127.0.0.1 works fine)
CWD=`echo $(dirname $(readlink -f $0))`

source ./db-env.sh

mysql --host ${DB_HOST} --port 3306 -u ${DB_USER} -p${DB_PASS} --database=${DB_DATABASE} --protocol=tcp

