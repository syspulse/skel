#!/bin/bash

RID=${1:-DetectorWallet}
RULE_FILE=${2}

NAME=${NAME}
OID=${OID-}
META=${META-}
META_FILE=${META_FILE-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/explain}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN`}

>&2 echo "RID=$RID"
>&2 echo "RULE_FILE=$RULE_FILE"
>&2 echo "NAME=$NAME"
>&2 echo "OID=$OID"
>&2 echo "META=$META"
>&2 echo "META_FILE=$META_FILE"

META_JSON=""
if [ -n "$META" ]; then
  META_JSON="$META"
elif [ -n "$META_FILE" ]; then
  if [ ! -f "$META_FILE" ]; then
    echo "ERROR: meta file not found: $META_FILE" >&2
    exit 1
  fi
  META_JSON=$(cat "$META_FILE")
fi

if [ -n "$META_JSON" ]; then
  if ! echo "$META_JSON" | jq -e . >/dev/null 2>&1; then
    echo "ERROR: META/META_FILE must be valid JSON" >&2
    exit 1
  fi
fi

if [ -n "$RULE_FILE" ]; then
  if [ ! -f "$RULE_FILE" ]; then
    echo "ERROR: rule file not found: $RULE_FILE" >&2
    exit 1
  fi
  DATA_JSON=$(cat "$RULE_FILE")
  if [ "$NAME" != "" ]; then
    DATA_JSON=$(echo "$DATA_JSON" | sed "s/\"name\":[^,}]*/\"name\": \"$NAME\"/")
  fi
else
  Q_NAME=""
  if [ "$NAME" != "" ]; then
    Q_NAME="\"name\": \"${NAME}\","
  fi
  read -r -d '' DATA_JSON << EOM
{
  ${Q_NAME}
  "ts": $(date +%s%3N)
}
EOM
fi

if [ -n "$META_JSON" ]; then
  DATA_JSON=$(echo "$DATA_JSON" | jq --argjson meta "$META_JSON" '. + {meta: $meta}')
fi

>&2 echo "$DATA_JSON"

if [ -n "$OID" ]; then
  URL="${SERVICE_URI}/${RID}?oid=${OID}"
else
  URL="${SERVICE_URI}/${RID}"
fi

curl -S -s -D /dev/stderr \
   -X PUT \
   -H 'Content-Type: application/json' \
   -H "Authorization: Bearer $ACCESS_TOKEN" \
   --data "$DATA_JSON" \
   "$URL"
