#!/bin/bash

TABLE=${1:-MOVIE}
TMP_SCHEMA=schema.json

source db-cred.sh
source db-endpoint.sh

aws dynamodb describe-table --table-name $TABLE $DB_URI | jq '.Table | {TableName, KeySchema, AttributeDefinitions} + (try {LocalSecondaryIndexes: [ .LocalSecondaryIndexes[] | {IndexName, KeySchema, Projection} ]} // {}) + (try {GlobalSecondaryIndexes: [ .GlobalSecondaryIndexes[] | {IndexName, KeySchema, Projection} ]} // {}) + {BillingMode: "PAY_PER_REQUEST"}' >$TMP_SCHEMA
aws dynamodb delete-table --table-name $TABLE $DB_URI 
aws dynamodb create-table --cli-input-json file://$TMP_SCHEMA $DB_URI