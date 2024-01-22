#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

export ALL_DB=yes
$CWD/db-sql-root.sh db-destroy.sql