#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

$CWD/db-sql.sh db-show.sql
