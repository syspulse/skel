#!/bin/bash
# --protocol=tcp is needed if connecting to localhost (127.0.0.1 works fine)
CWD=`echo $(dirname $(readlink -f $0))`

source db-env.sh

echo "DB_USER=$DB_USER"
echo "DB_PASS=$DB_PASS"
echo "DB_DATABASE=$DB_DATABASE"
echo "DB_HOST=$DB_HOST"

SQL_FILE=${1}

PGPASSWORD=$ROOT_PASS psql --host=$DB_HOST --port=5432 --username=$ROOT_USER -d $DB_DATABASE -f ${SQL_FILE}