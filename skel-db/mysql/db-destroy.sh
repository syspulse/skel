#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

$CWD/db-sql-root.sh db-destroy.sql
