#!/bin/bash
# Delete Event / Alert by Alert eid.
#   ./wf-event-eid-del.sh <eid>
#   ./wf-event-eid-del.sh e-one
#   EID=e-one ./wf-event-eid-del.sh
#   OID=490 ./wf-event-eid-del.sh e-one
EID=${1:-${EID:-}}
OID=${OID:-490}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

if [[ -z "$EID" ]]; then
  >&2 echo "usage: $0 <eid>   # DELETE /event/eid/{eid}"
  exit 1
fi

URL="$SERVICE_URI/event/eid/${EID}"
[[ -n "$OID" ]] && URL="${URL}?oid=${OID}"

>&2 echo "URL: $URL"
curl -S -s -D /dev/stderr -X DELETE -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
