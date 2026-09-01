#!/bin/bash

source ~/prj/hacken/extractor/ext2/auth/env.dev

export DB_USER=$DB_USER_EXT
export DB_PASS=$DB_PASS_EXT
export DB_DATABASE=$DB_DATABASE_EXT
export DB_URL=$DB_URL_EXT


DATASTORE=${DATASTORE:-postgres://}

exec ./run-wf.sh \
   --conf=conf/application-dev.conf \
   --jwt.uri=https://auth.dev.extractor.live/realms/hacken/.well-known/openid-configuration \
   --datastore="$DATASTORE" \
   --engine.uri="temporal://$TEMPORAL_GRPC/ext_workflows?tls=ignore&auth=${ACCESS_TOKEN_TEMPORAL}" \
   --engine.url="http://localhost:7233"
   "$@"
