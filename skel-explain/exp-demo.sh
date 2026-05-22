#!/bin/bash

set -e

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/explain}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null || echo ""`}
RID=${RID:-DetectorWallet}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RULES_DIR="${SCRIPT_DIR}/rules"

sep() { echo; echo "────────────────────────────────────────────"; echo "  $*"; echo "────────────────────────────────────────────"; }
ok()  { echo "[OK] $*"; }
fail(){ echo "[FAIL] $*" >&2; exit 1; }

call() {
  local label="$1"; shift
  echo
  echo ">>> $label"
  local out
  out=$(curl -S -s -w "\n__STATUS__:%{http_code}" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    "$@")
  local status
  status=$(echo "$out" | grep '__STATUS__:' | sed 's/__STATUS__://')
  local body
  body=$(echo "$out" | grep -v '__STATUS__:')
  echo "$body"
  echo "<<< HTTP $status"
  LAST_BODY="$body"
  LAST_STATUS="$status"
}

DATA='{"address":"0x9000000000000000000000000000000000000000","metadata":{"tx_from":"0xA911Ff351B143634Dbc5aF3E204EA074583A83e3","balance":100,"threshold":"> 1000.0","wallet":"0x9000000000000000000000000000000000000000"}}'

sep "1. CREATE rule for rid=$RID"
call "POST /$RID" \
  -X POST \
  --data "@${RULES_DIR}/Rule-${RID}.json" \
  "$SERVICE_URI/$RID"
[[ "$LAST_STATUS" == "200" || "$LAST_STATUS" == "201" ]] && ok "rule created" || fail "create rule failed (HTTP $LAST_STATUS)"

sep "2. GET rule"
call "GET /$RID" \
  -X GET \
  "$SERVICE_URI/$RID"
[[ "$LAST_STATUS" == "200" ]] && ok "rule retrieved" || fail "get rule failed (HTTP $LAST_STATUS)"

sep "3. UPDATE rule"
call "PUT /$RID" \
  -X PUT \
  --data "@${RULES_DIR}/Rule-${RID}-updated.json" \
  "$SERVICE_URI/$RID"
[[ "$LAST_STATUS" == "200" ]] && ok "rule updated" || fail "update rule failed (HTTP $LAST_STATUS)"

sep "4. EXPLAIN (no style)"
call "GET /$RID/explain" \
  -X GET \
  --data "{\"data\": $DATA}" \
  "$SERVICE_URI/$RID/explain"
[[ "$LAST_STATUS" == "200" ]] && ok "explain succeeded" || fail "explain failed (HTTP $LAST_STATUS)"

sep "5. EXPLAIN style=short"
call "GET /$RID/explain?style=short" \
  -X GET \
  --data "{\"data\": $DATA}" \
  "$SERVICE_URI/$RID/explain?style=short"
[[ "$LAST_STATUS" == "200" ]] && ok "explain (short) succeeded" || fail "explain (short) failed (HTTP $LAST_STATUS)"

sep "6. EXPLAIN style=narrative"
call "GET /$RID/explain?style=narrative" \
  -X GET \
  --data "{\"data\": $DATA}" \
  "$SERVICE_URI/$RID/explain?style=narrative"
[[ "$LAST_STATUS" == "200" ]] && ok "explain (narrative) succeeded" || fail "explain (narrative) failed (HTTP $LAST_STATUS)"

sep "7. EXPLAIN style=detailed"
call "GET /$RID/explain?style=detailed" \
  -X GET \
  --data "{\"data\": $DATA}" \
  "$SERVICE_URI/$RID/explain?style=detailed"
[[ "$LAST_STATUS" == "200" ]] && ok "explain (detailed) succeeded" || fail "explain (detailed) failed (HTTP $LAST_STATUS)"

sep "8. EXPLAIN after rule deleted → expect error"
call "DELETE /$RID" \
  -X DELETE \
  "$SERVICE_URI/$RID"
[[ "$LAST_STATUS" == "200" ]] && ok "rule deleted" || fail "delete rule failed (HTTP $LAST_STATUS)"

call "GET /$RID/explain (no rule — expect error)" \
  -X GET \
  --data "{\"data\": $DATA}" \
  "$SERVICE_URI/$RID/explain"
[[ "$LAST_STATUS" == "404" || "$LAST_STATUS" == "400" ]] && ok "correctly returned error (HTTP $LAST_STATUS)" || echo "[WARN] unexpected status $LAST_STATUS (expected 404/400)"

sep "DEMO COMPLETE"
echo
echo "All steps passed."
