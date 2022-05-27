#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`
cd $CWD

./db-sql.sh db-show.sql
