#!/bin/bash

DATASTORE=${DATASTORE:-postgres://}
export GOD=1

exec ./run-wf.sh \
   --datastore="$DATASTORE" \
   --engine="temporal://$TEMPORAL_GRPC/ext_workflows?tls=ignore&auth=${ACCESS_TOKEN_TEMPORAL}" \
   "$@"
