#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

source db-env.sh

ALL_DB=true $CWD/db-sql.sh db-create.sql
