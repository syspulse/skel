#!/bin/bash
# ATTENTION: -shardDb is very important 
# (otherwise other clients will not be able to see Tables"
# Notes on AWS_REGION/AWS_ACCESS_KEY_ID
# https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.UsageNotes.html
docker run --name dynamodb --rm -p 8100:8000 -d amazon/dynamodb-local -jar DynamoDBLocal.jar -sharedDb
docker logs -f dynamodb
