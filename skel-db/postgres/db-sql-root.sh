#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

source db-env.sh

echo "DB_USER=$DB_USER"
echo "DB_PASS=$DB_PASS"
echo "DB_DATABASE=$DB_DATABASE"
echo "DB_HOST=$DB_HOST"

SQL_FILE=${1}

# pass the env credentials to the SQL as psql variables (so *.sql never hardcode user/pass/db):
#   :"DB_USER" / :"DB_DATABASE" -> quoted identifier;  :'DB_PASS' -> quoted string literal
PSQL_VARS="-v DB_USER=${DB_USER} -v DB_PASS=${DB_PASS} -v DB_DATABASE=${DB_DATABASE}"

if [ "$ALL_DB" != "" ]; then
PGPASSWORD=$ROOT_PASS psql --host=$DB_HOST --port=5432 --username=$ROOT_USER $PSQL_VARS -f ${SQL_FILE}
else
PGPASSWORD=$ROOT_PASS psql --host=$DB_HOST --port=5432 --username=$ROOT_USER -d $DB_DATABASE $PSQL_VARS -f ${SQL_FILE}
fi
