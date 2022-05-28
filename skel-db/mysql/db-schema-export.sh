#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

source db-env.sh

mysqldump -d -u ${DB_USER} -p${DB_PASS} -h ${DB_HOST} ${DB_DATABASE} >${DB_DATABASE}_schema.sql