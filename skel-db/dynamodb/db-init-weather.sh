#!/bin/bash

source db-cred.sh
source db-endpoint.sh

aws dynamodb create-table \
    --table-name WeatherForecast \
    --attribute-definitions \
        AttributeName=City,AttributeType=S \
        AttributeName=Date,AttributeType=S \
    --key-schema AttributeName=City,KeyType=HASH AttributeName=Date,KeyType=RANGE \
    --provisioned-throughput ReadCapacityUnits=1,WriteCapacityUnits=1 \
    $DB_URI

aws dynamodb list-tables $DB_URI
