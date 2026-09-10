#!/bin/bash

export DB_USER=workflow_user
export DB_PASS=workflow_pass
export DB_DATABASE=${DB_DATABASE:-workflow_db}

export ELASTIC_USER=admin
export ELASTIC_PASS=Abcd_1234#


DATASTORE=${DATASTORE:-postgres://postgres0}

export GOD=1

exec ./run-wf.sh \      
   --datastore="$DATASTORE" \
   --engine.uri="temporal://" \
   --engine.url="http://localhost:8080" \
   --elastic.uri="http://localhost:9200" \
   "$@"
