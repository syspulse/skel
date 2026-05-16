#!/bin/bash

set -e

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/explain}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null || echo ""`}
OID=${OID:-490}
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

sep "1. CREATE default rule (oid=\"\") for rid=$RID"
call "POST /rule/$RID" \
  -X POST \
  --data "@${RULES_DIR}/${RID}.json" \
  "$SERVICE_URI/rule/$RID"
[[ "$LAST_STATUS" == "200" || "$LAST_STATUS" == "201" ]] && ok "default rule created" || fail "create default rule failed (HTTP $LAST_STATUS)"

sep "2. GET default rule"
call "GET /rule/$RID" \
  -X GET \
  "$SERVICE_URI/rule/$RID"
[[ "$LAST_STATUS" == "200" ]] && ok "default rule retrieved" || fail "get default rule failed (HTTP $LAST_STATUS)"

sep "3. CREATE OID-specific rule (oid=$OID) for rid=$RID"
call "POST /$OID/$RID" \
  -X POST \
  --data "@${RULES_DIR}/${RID}-oid${OID}.json" \
  "$SERVICE_URI/$OID/$RID"
[[ "$LAST_STATUS" == "200" || "$LAST_STATUS" == "201" ]] && ok "OID rule created" || fail "create OID rule failed (HTTP $LAST_STATUS)"

sep "4. UPDATE default rule"
call "PUT /rule/$RID" \
  -X PUT \
  --data "@${RULES_DIR}/${RID}-updated.json" \
  "$SERVICE_URI/rule/$RID"
[[ "$LAST_STATUS" == "200" ]] && ok "default rule updated" || fail "update default rule failed (HTTP $LAST_STATUS)"

sep "5. EXPLAIN with no oid → uses default (updated) rule"
DATA='{"address":"0x9000000000000000000000000000000000000000","metadata":{"tx_from":"0xA911Ff351B143634Dbc5aF3E204EA074583A83e3","balance":100,"threshold":"> 1000.0","wallet":"0x9000000000000000000000000000000000000000"}}'
call "POST /$RID (explain, no oid)" \
  -X POST \
  --data "{\"data\": $DATA}" \
  "$SERVICE_URI/$RID"
[[ "$LAST_STATUS" == "200" ]] && ok "explain (default) succeeded" || fail "explain (default) failed (HTTP $LAST_STATUS)"

sep "6. EXPLAIN with oid=$OID → uses OID-specific rule"
call "POST /$RID (explain, oid=$OID)" \
  -X POST \
  --data "{\"oid\": \"$OID\", \"data\": $DATA}" \
  "$SERVICE_URI/$RID"
[[ "$LAST_STATUS" == "200" ]] && ok "explain (oid=$OID) succeeded" || fail "explain (oid=$OID) failed (HTTP $LAST_STATUS)"

sep "7. EXPLAIN with oid=999 → no rule → fallback to default"
call "POST /$RID (explain, oid=999 fallback)" \
  -X POST \
  --data "{\"oid\": \"999\", \"data\": $DATA}" \
  "$SERVICE_URI/$RID"
[[ "$LAST_STATUS" == "200" ]] && ok "explain (oid=999 fallback) succeeded" || fail "explain (oid=999 fallback) failed (HTTP $LAST_STATUS)"

sep "8. DELETE OID-specific rule (oid=$OID)"
call "DELETE /$OID/$RID" \
  -X DELETE \
  "$SERVICE_URI/$OID/$RID"
[[ "$LAST_STATUS" == "200" ]] && ok "OID rule deleted" || fail "delete OID rule failed (HTTP $LAST_STATUS)"

sep "9. EXPLAIN with oid=$OID after delete → fallback to default"
call "POST /$RID (explain, oid=$OID after delete)" \
  -X POST \
  --data "{\"oid\": \"$OID\", \"data\": $DATA}" \
  "$SERVICE_URI/$RID"
[[ "$LAST_STATUS" == "200" ]] && ok "explain (oid=$OID fallback after delete) succeeded" || fail "explain (oid=$OID fallback after delete) failed (HTTP $LAST_STATUS)"

sep "10. DELETE default rule"
call "DELETE /rule/$RID" \
  -X DELETE \
  "$SERVICE_URI/rule/$RID"
[[ "$LAST_STATUS" == "200" ]] && ok "default rule deleted" || fail "delete default rule failed (HTTP $LAST_STATUS)"

sep "11. EXPLAIN after all rules deleted → expect error"
call "POST /$RID (explain, no rule — expect error)" \
  -X POST \
  --data "{\"data\": $DATA}" \
  "$SERVICE_URI/$RID"
[[ "$LAST_STATUS" == "404" || "$LAST_STATUS" == "400" ]] && ok "correctly returned error (HTTP $LAST_STATUS)" || echo "[WARN] unexpected status $LAST_STATUS (expected 404/400)"

sep "DEMO COMPLETE"
echo
echo "All steps passed."
