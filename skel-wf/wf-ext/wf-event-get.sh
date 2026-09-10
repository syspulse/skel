#!/bin/bash
# Get Event / Alert by Elastic _id (`{did}:{eid}`), or by Alert eid.
#   ./wf-event-get.sh <id>
#   ./wf-event-get.sh 22587:e-one
#   EID=e-one ./wf-event-get.sh
#   OID=490 ./wf-event-get.sh 22587:e-one
ID=${1:-}
EID=${EID:-}
OID=${OID:-}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

append_qp() {
  local url=$1
  local q=""
  [[ -n "$OID" ]] && q="${q:+$q&}oid=${OID}"
  if [[ -n "$q" ]]; then
    [[ "$url" == *\?* ]] && echo "${url}&${q}" || echo "${url}?${q}"
  else
    echo "$url"
  fi
}

if [[ -n "$ID" ]]; then
  URL=$(append_qp "$SERVICE_URI/event/${ID}")
elif [[ -n "$EID" ]]; then
  URL=$(append_qp "$SERVICE_URI/event/eid/${EID}")
else
  >&2 echo "usage: $0 <elastic-id>   # GET /event/{did}:{eid}"
  >&2 echo "       EID=<eid> $0      # GET /event/eid/{eid}"
  exit 1
fi

>&2 echo "URL: $URL"
curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
