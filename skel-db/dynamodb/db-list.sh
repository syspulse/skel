#!/bin/bash

source db-cred.sh
source db-endpoint.sh

aws dynamodb list-tables $DB_URI
