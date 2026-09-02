#!/bin/bash
#
# Ful Dev env
#

source ~/prj/hacken/extractor/ext2/auth/env.dev

export DB_USER=$DB_USER_EXT
export DB_PASS=$DB_PASS_EXT
export DB_DATABASE=$DB_DATABASE_EXT
export DB_URL=$DB_URL_EXT

export ACCESS_TOKEN_TEMPORAL=`cat ACCESS_TOKEN_TEMPORAL`

DATASTORE=${DATASTORE:-postgres://}

echo TEMPORAL_GRPC=$TEMPORAL_GRPC
echo TEMPORAL_URL=$TEMPORAL_URL
echo ACCESS_TOKEN_TEMPORAL=$ACCESS_TOKEN_TEMPORAL

exec ./run-wf.sh \
   --conf=conf/application-dev.conf \
   --jwt.uri=https://auth.dev.extractor.live/realms/hacken/.well-known/openid-configuration \
   --datastore="$DATASTORE" \
   "$@"
