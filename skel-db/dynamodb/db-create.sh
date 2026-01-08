#!/bin/bash

SCHEMA=${1:-schema-MOVIE.json}

source db-cred.sh
source db-endpoint.sh

aws dynamodb create-table \
    --cli-input-json file://${SCHEMA} \
    $DB_URL

aws dynamodb list-tables $DB_URL
