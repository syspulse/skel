#!/bin/bash

# Start PoR Temporal Worker

export CWD=`echo $(dirname $(readlink -f $0))`

export SITE=${SITE:-temporal}
export TEMPORAL_SERVICE_ADDRESS=${TEMPORAL_SERVICE_ADDRESS:-127.0.0.1:7233}

echo "Starting PoR Worker..."
echo "Temporal Service: $TEMPORAL_SERVICE_ADDRESS"

exec ${CWD}/run-temporal.sh por-worker $@
