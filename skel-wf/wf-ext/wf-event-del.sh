#!/bin/bash
# Delete Event / Alert by Elastic _id (`{did}:{eid}`).
#   ./wf-event-del.sh <id>
#   ./wf-event-del.sh 22587:e-one
#   OID=490 ./wf-event-del.sh 22587:e-one
#   ID=22587:e-one ./wf-event-del.sh
ID=${1:-${ID:-}}
OID=${OID:-490}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

if [[ -z "$ID" ]]; then
  >&2 echo "usage: $0 <elastic-id>   # DELETE /event/{did}:{eid}"
  exit 1
fi

URL="$SERVICE_URI/event/${ID}"
[[ -n "$OID" ]] && URL="${URL}?oid=${OID}"

>&2 echo "URL: $URL"
curl -S -s -D /dev/stderr -X DELETE -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
