#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

source db-env.sh

echo "DB_USER=$DB_USER"
echo "DB_PASS=$DB_PASS"
echo "DB_DATABASE=$DB_DATABASE"
echo "DB_HOST=$DB_HOST"

SQL_FILE=${1}

# pass the env credentials to the SQL as psql variables (so *.sql never hardcode user/pass/db)
PSQL_VARS="-v DB_USER=${DB_USER} -v DB_PASS=${DB_PASS} -v DB_DATABASE=${DB_DATABASE}"

if [ "$ALL_DB" != "" ]; then
PGPASSWORD=$DB_PASS psql --host=$DB_HOST --port=5432 --username=$DB_USER $PSQL_VARS -f ${SQL_FILE}
else
PGPASSWORD=$DB_PASS psql --host=$DB_HOST --port=5432 --username=$DB_USER -d $DB_DATABASE $PSQL_VARS -f ${SQL_FILE}
fi
