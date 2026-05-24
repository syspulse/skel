#!/bin/bash

set -e

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/explain}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null || echo ""`}

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RULES_DIR="${SCRIPT_DIR}/rules"

ok()  { echo "[OK] $*"; }
fail(){ echo "[FAIL] $*" >&2; exit 1; }

LOADED=0
FAILED=0

for rule_file in "${RULES_DIR}"/Rule-*.json; do
  base="$(basename "$rule_file")"
  rid="${base#Rule-}"
  rid="${rid%-default.json}"
  rid="${rid%.json}"

  echo
  echo ">>> Creating rule: rid='$rid'  file='$base'"

  out=$(curl -S -s -w "\n__STATUS__:%{http_code}" \
    -X POST \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    --data "@${rule_file}" \
    "${SERVICE_URI}/${rid}")

  status=$(echo "$out" | grep '__STATUS__:' | sed 's/__STATUS__://')
  body=$(echo "$out" | grep -v '__STATUS__:')
  echo "$body"
  echo "<<< HTTP $status"

  if [[ "$status" == "200" || "$status" == "201" ]]; then
    ok "$rid"
    LOADED=$((LOADED + 1))
  else
    echo "[FAIL] $rid (HTTP $status)" >&2
    FAILED=$((FAILED + 1))
  fi
done

echo
echo "────────────────────────────────────────────"
echo "  Loaded: $LOADED  Failed: $FAILED"
echo "────────────────────────────────────────────"

[ "$FAILED" -eq 0 ] || exit 1
