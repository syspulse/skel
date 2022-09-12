#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

source db-env.sh

export ALL_DB=true 
export DB_USER=$ROOT_USER 
export DB_PASS=$ROOT_PASS 

mysql --host ${DB_HOST} --port 3306 -u ${DB_USER} -p${DB_PASS} --protocol=tcp < db-create.sql
