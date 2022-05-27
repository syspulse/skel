#!/bin/bash

ROOT_USER=postgres
ROOT_PASS=root_pass

#DB_INSTANCE=test_db
#DB_DATABASE=test_db

DB_USER=test_user
DB_PASS=test_pass

DB_HOST=localhost

#PGPASSWORD=$ROOT_PASS psql --host=$DB_HOST --port=5432 --username=$ROOT_USER --dbname=$DB_INSTANCE -f db-create.sql
PGPASSWORD=$ROOT_PASS psql --host=$DB_HOST --port=5432 --username=$ROOT_USER -f db-create.sql
