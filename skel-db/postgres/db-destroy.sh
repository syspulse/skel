#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD
source ./db-env.sh

# test_db is some db (created by docker-compose automatically) because postgres has these stupid anachronism
PGPASSWORD=$ROOT_PASS psql --host=$DB_HOST --port=5432 --username=$ROOT_USER -d test_db -f db-destroy.sql
