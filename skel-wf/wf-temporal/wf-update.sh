#!/bin/bash

WID=${1}
NAME=${2}

if [ -z "$WID" ]; then
  >&2 echo "Usage: $0 <workflow-id> [name] [TITLE=title] [DESC=description]"
  exit 1
fi

TITLE=${TITLE}
DESC=${DESC}
VERSION=${VERSION}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf}

>&2 echo "WID=$WID"
>&2 echo "NAME=$NAME"
>&2 echo "TITLE=$TITLE"
>&2 echo "DESC=$DESC"
>&2 echo "VERSION=$VERSION"

# Build JSON dynamically based on what's provided
DATA_JSON="{"
FIRST=1

if [ ! -z "$NAME" ]; then
  DATA_JSON="${DATA_JSON}\"name\": \"${NAME}\""
  FIRST=0
fi

if [ ! -z "$TITLE" ]; then
  [ $FIRST -eq 0 ] && DATA_JSON="${DATA_JSON},"
  DATA_JSON="${DATA_JSON}\"title\": \"${TITLE}\""
  FIRST=0
fi

if [ ! -z "$DESC" ]; then
  [ $FIRST -eq 0 ] && DATA_JSON="${DATA_JSON},"
  DATA_JSON="${DATA_JSON}\"description\": \"${DESC}\""
  FIRST=0
fi

if [ ! -z "$VERSION" ]; then
  [ $FIRST -eq 0 ] && DATA_JSON="${DATA_JSON},"
  DATA_JSON="${DATA_JSON}\"version\": \"${VERSION}\""
  FIRST=0
fi

DATA_JSON="${DATA_JSON}}"

>&2 echo "$DATA_JSON"
>&2 echo "PUT $SERVICE_URI/schema/$WID"

curl -S -s -D /dev/stderr \
   -X PUT \
   -H 'Content-Type: application/json' \
   --data "$DATA_JSON" \
   $SERVICE_URI/schema/${WID}
