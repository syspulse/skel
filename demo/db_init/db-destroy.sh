#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

$CWD/db-sql.sh db-destroy.sql