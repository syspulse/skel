#!/bin/bash

TABLE=${1:-MOVIE}

source db-cred.sh
source db-endpoint.sh

aws dynamodb describe-table \
   --table-name $TABLE \
   $DB_URI | jq '.Table | {TableName, KeySchema, AttributeDefinitions} + (try {LocalSecondaryIndexes: [ .LocalSecondaryIndexes[] | {IndexName, KeySchema, Projection} ]} // {}) + (try {GlobalSecondaryIndexes: [ .GlobalSecondaryIndexes[] | {IndexName, KeySchema, Projection} ]} // {}) + {BillingMode: "PAY_PER_REQUEST"}' 
