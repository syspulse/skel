#!/bin/bash

TABLE=${1:-MOVIE}
DB_URL=${DB_URL:-http://localhost:8100}

source db-cred.sh
source db-endpoint.sh

aws dynamodb scan --table-name $TABLE $DB_URL
