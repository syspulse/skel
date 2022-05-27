#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD
source ./db-env.sh

PGPASSWORD=$ROOT_PASS psql --host=$DB_HOST --port=5432 --username=$ROOT_USER -f db-create.sql
