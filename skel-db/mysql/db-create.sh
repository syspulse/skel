#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

source db-env.sh

$CWD/db-sql.sh db-create.sql
