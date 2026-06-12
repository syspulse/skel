#!/bin/bash
# Create a WorkflowSchema. Pass a JSON body file, or use the DSL endpoint.
#   ./wf-schema-create.sh '{"name":"WorkflowAudit"}'
#   DSL='Detector.a -> Detector.b' ./wf-schema-create.sh
NAME=${1:-WorkflowDemo}
DSL=${DSL:-}
WID=${WID:-}
WN=${WN:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

if [[ -n "$DSL" ]]; then
  BODY="{\"pipeline\":\"$DSL\""
  [[ -n "$WID" ]] && BODY="$BODY,\"wid\":$WID"
  [[ -n "$WN" ]]  && BODY="$BODY,\"name\":\"$WN\""
  BODY="$BODY}"
  URL="$SERVICE_URI/schema/dsl"
else
  BODY="{\"name\":\"$NAME\"}"
  URL="$SERVICE_URI/schema"
fi

>&2 echo "$BODY"
curl -S -s -D /dev/stderr -X POST --data "$BODY" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
