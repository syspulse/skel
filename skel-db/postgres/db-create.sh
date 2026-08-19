#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

CREATE_SQL=${1:-db-create.sql}

source db-env.sh

ALL_DB=true $CWD/db-sql-root.sh $CREATE_SQL

if [ -e "db-schema.sql" ]; then
  $CWD/db-sql-root.sh db-schema.sql
fi

