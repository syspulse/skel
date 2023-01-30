#!/bin/bash
# ATTENTION: -shardDb is very important 
# (otherwise other clients will not be able to see Tables"
# Notes on AWS_REGION/AWS_ACCESS_KEY_ID
# https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.UsageNotes.html
DYNAMO_PORT=${DYNAMO_PORT:-8100}
echo "port: $DYNAMO_PORT"
docker run --name dynamodb --rm -p ${DYNAMO_PORT}:8000 -d amazon/dynamodb-local -jar DynamoDBLocal.jar -sharedDb
docker logs -f dynamodb

