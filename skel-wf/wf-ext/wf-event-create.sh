#!/bin/bash
# Create Event(s) (POST /event). Body is one object or an array. Same did+eid overwrites.
#   ./wf-event-create.sh
#   ./wf-event-create.sh event.json
#   ./wf-event-create.sh '{"ts":1,"eid":"e1","oid":490,"pid":1,"did":22587,"nid":"N","sev":0.25}'
#   EID=e1 OID=490 PID=1789 DID=22587 NID=SafeMultisigMonitor SEV=0.25 TAGS=WORKFLOW,COMPLIANCE ./wf-event-create.sh
#   OID=490 ./wf-event-create.sh     # ?oid= stamped for users / overrides for admin
ARG=${1:-}
OID=${OID:-490}
PID=${PID:-474}
DID=${DID:-22587}
EID=${EID:-}
RID=${RID:-}
NID=${NID:-SafeMultisigMonitor}
NAME=${NAME:-}
WID=${WID:-}
SID=${SID:-WORKFLOW}
SEV=${SEV:-0.25}
DESC=${DESC:-}
TS=${TS:-}
META=${META:-}
TAGS=${TAGS:-WORKFLOW,COMPLIANCE}

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

if [[ -n "$ARG" && -f "$ARG" ]]; then
  BODY=$(cat "$ARG")
elif [[ "$ARG" == \{* || "$ARG" == \[* ]]; then
  BODY="$ARG"
else
  [[ -z "$TS" ]] && TS=$(date +%s%3N 2>/dev/null || echo $(( $(date +%s) * 1000 )))
  [[ -z "$EID" ]] && EID="e-${TS}"
  BODY="{\"ts\":${TS},\"eid\":\"${EID}\",\"did\":${DID},\"pid\":${PID},\"nid\":\"${NID}\",\"sev\":${SEV}"
  [[ -n "$OID" ]]  && BODY="${BODY},\"oid\":${OID}"
  [[ -z "$OID" ]]  && BODY="${BODY},\"oid\":0"
  [[ -n "$RID" ]]  && BODY="${BODY},\"rid\":\"${RID}\""
  [[ -n "$NAME" ]] && BODY="${BODY},\"name\":\"${NAME}\""
  [[ -n "$WID" ]]  && BODY="${BODY},\"wid\":\"${WID}\""
  [[ -n "$SID" ]]  && BODY="${BODY},\"sid\":\"${SID}\""
  [[ -n "$DESC" ]] && BODY="${BODY},\"desc\":\"${DESC}\""
  [[ -n "$META" ]] && BODY="${BODY},\"meta\":${META}"
  if [[ -n "$TAGS" ]]; then
    TAGS_JSON=""
    IFS=',' read -ra TAG_ARR <<< "$TAGS"
    for t in "${TAG_ARR[@]}"; do
      t="${t#"${t%%[![:space:]]*}"}"
      t="${t%"${t##*[![:space:]]}"}"
      [[ -z "$t" ]] && continue
      TAGS_JSON="${TAGS_JSON:+$TAGS_JSON,}\"${t}\""
    done
    [[ -n "$TAGS_JSON" ]] && BODY="${BODY},\"tags\":[${TAGS_JSON}]"
  fi
  BODY="${BODY}}"
fi

URL=$(append_qp "$SERVICE_URI/event")

>&2 echo "$BODY"
>&2 echo "URL: $URL"
curl -S -s -D /dev/stderr -X POST --data "$BODY" -H 'Content-Type: application/json' -H "Authorization: Bearer $ACCESS_TOKEN" "$URL"
