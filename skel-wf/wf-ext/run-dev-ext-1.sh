#!/bin/bash

DATASTORE=${DATASTORE:-postgres://}

exec ./run-wf.sh \
   --conf=conf/application-dev.conf \
   --jwt.uri=https://auth.dev.extractor.live/realms/hacken/.well-known/openid-configuration \
   --datastore="$DATASTORE" \
   --engine.uri="temporal://$TEMPORAL_GRPC/ext_workflows?tls=ignore&auth=${ACCESS_TOKEN_TEMPORAL}" \
   "$@"
