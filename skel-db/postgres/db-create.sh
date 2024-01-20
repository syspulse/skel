#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

source db-env.sh

ALL_DB=true $CWD/db-sql-root.sh db-create.sql
