#!/bin/bash
# Query Events / Alerts.
#   ./wf-event-query.sh
#   OID=490 FROM=0 SIZE=10 ./wf-event-query.sh
#   OID=490 PID=1789 DID=12913 SID=WORKFLOW TS0=1606311430000 TS1=1606311439999 ./wf-event-query.sh
# Default window: ts0 = now-1d, ts1 = now (epoch ms)
OID=${OID:-490}
PID=${PID:-474}
DID=${DID:-}
SID=${SID:-}
NOW_MS=$(date +%s%3N 2>/dev/null || echo $(( $(date +%s) * 1000 )))
TS0=${TS0:-$(( NOW_MS - 86400000 ))}
TS1=${TS1:-$NOW_MS}
FROM=${FROM:-0}
SIZE=${SIZE:-10}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null`}

Q=""
[[ -n "$FROM" ]] && Q="${Q:+$Q&}from=${FROM}"
[[ -n "$SIZE" ]] && Q="${Q:+$Q&}size=${SIZE}"
[[ -n "$OID" ]]  && Q="${Q:+$Q&}oid=${OID}"
[[ -n "$PID" ]]  && Q="${Q:+$Q&}pid=${PID}"
[[ -n "$DID" ]]  && Q="${Q:+$Q&}did=${DID}"
[[ -n "$SID" ]]  && Q="${Q:+$Q&}sid=${SID}"
[[ -n "$TS0" ]]  && Q="${Q:+$Q&}ts0=${TS0}"
[[ -n "$TS1" ]]  && Q="${Q:+$Q&}ts1=${TS1}"

URL="$SERVICE_URI/event"
[[ -n "$Q" ]] && URL="${URL}?${Q}"

>&2 echo "URL: $URL"
curl -S -s -D /dev/stderr -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
