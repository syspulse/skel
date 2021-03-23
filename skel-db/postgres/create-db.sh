#!/bin/bash

ROOT_USER=root
ROOT_PASS=309d5a3c5987f77fc5952f15b20a1818161df57c
DB_INSTANCE=bip

DB_USER=shop_user
DB_PASS=shop_pass
DB_DATABASE=shop_db

#DB_HOST=localhost
DB_HOST=mydb1.cpmw9srbc1xl.eu-west-1.rds.amazonaws.com

PGPASSWORD=$ROOT_PASS psql --host=$DB_HOST --port=5432 --username=$ROOT_USER --dbname=$DB_INSTANCE -f create-db.sql
