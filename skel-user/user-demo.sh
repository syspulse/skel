#!/bin/bash
set -euo pipefail

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/user}
ACCESS_TOKEN=${ACCESS_TOKEN:-}
if [[ -z "${ACCESS_TOKEN}" && -f "ACCESS_TOKEN" ]]; then
  ACCESS_TOKEN="$(cat ACCESS_TOKEN)"
fi

need() { command -v "$1" >/dev/null 2>&1 || { echo "missing dependency: $1" >&2; exit 1; }; }

need curl
need jq
need hexdump

die() { echo "ERROR: $*" >&2; exit 1; }

log() { echo "==> $*"; }

rand_hex8() { hexdump -n 4 -v -e '/1 "%02x"' /dev/urandom; }

curl_api() {
  # curl_api <METHOD> <PATH> [JSON_BODY]
  local method="$1"
  local path="$2"
  local body="${3:-}"
  local url="${SERVICE_URI%/}${path}"

  local -a headers
  headers=(-H 'Content-Type: application/json')
  [[ -n "${ACCESS_TOKEN}" ]] && headers+=(-H "Authorization: Bearer ${ACCESS_TOKEN}")

  if [[ -n "${body}" ]]; then
    curl -sS -X "${method}" "${headers[@]}" --data "${body}" "${url}" -w $'\n%{http_code}\n'
  else
    curl -sS -X "${method}" "${headers[@]}" "${url}" -w $'\n%{http_code}\n'
  fi
}

resp_body() { sed '$d'; }
resp_code() { tail -n 1; }

expect_code() {
  local got="$1"
  local want="$2"
  [[ "$got" == "$want" ]] || die "unexpected status code: got=$got want=$want"
}

expect_json() { jq -e . >/dev/null || die "response is not valid JSON"; }

create_user() {
  local email="$1"
  local resp code body id
  resp="$(curl_api POST "/" "$(jq -cn --arg email "$email" '{email:$email}')")"
  code="$(printf "%s" "$resp" | resp_code)"
  body="$(printf "%s" "$resp" | resp_body)"
  expect_code "$code" "201"
  printf "%s" "$body" | expect_json
  id="$(printf "%s" "$body" | jq -er '.id')"
  printf "%s" "$id"
}

update_user() {
  local id="$1"
  local patch="$2" # JSON object
  local resp code body
  resp="$(curl_api PUT "/${id}" "$patch")"
  code="$(printf "%s" "$resp" | resp_code)"
  body="$(printf "%s" "$resp" | resp_body)"
  expect_code "$code" "200"
  printf "%s" "$body" | expect_json
  # verify the updated entity matches the id
  printf "%s" "$body" | jq -er --arg id "$id" '.id == $id' >/dev/null || die "update returned wrong id"
}

delete_user() {
  local id="$1"
  local resp code
  resp="$(curl_api DELETE "/${id}")"
  code="$(printf "%s" "$resp" | resp_code)"
  # service may return 200 or 404/619-style. We only assert it is not 500-class.
  [[ "$code" =~ ^[45]..$ ]] && [[ "$code" != 404 && "$code" != 400 ]] && die "delete failed with status $code"
}

get_users_page() {
  local from="$1"
  local size="$2"
  local resp code body
  resp="$(curl_api GET "/?from=${from}&size=${size}")"
  code="$(printf "%s" "$resp" | resp_code)"
  body="$(printf "%s" "$resp" | resp_body)"
  expect_code "$code" "200"
  printf "%s" "$body" | expect_json
  printf "%s" "$body"
}

get_all_users() {
  local resp code body
  resp="$(curl_api GET "/")"
  code="$(printf "%s" "$resp" | resp_code)"
  body="$(printf "%s" "$resp" | resp_body)"
  expect_code "$code" "200"
  printf "%s" "$body" | expect_json
  printf "%s" "$body"
}

ids_from_users() {
  jq -er '.users[]?.id'
}

step_create_100() {
  log "Creating 100 users..."
  IDS=()
  for _ in $(seq 1 100); do
    local r email id
    r="$(rand_hex8)"
    email="demo-${r}@example.com"
    id="$(create_user "$email")"
    IDS+=("$id")
  done
  [[ "${#IDS[@]}" -eq 100 ]] || die "expected 100 created users, got ${#IDS[@]}"
  log "Created: ${#IDS[@]}"
}

step_update_5() {
  log "Updating 5 users with different attributes..."
  for idx in $(seq 0 4); do
    local id r patch
    id="${IDS[$idx]}"
    r="$(rand_hex8)"
    case "$idx" in
      0) patch="$(jq -cn --arg v "Name-${r}" '{name:$v}')" ;;
      1) patch="$(jq -cn --arg v "https://example.com/icon/${r}.png" '{avatar:$v}')" ;;
      2) patch="$(jq -cn --arg v "updated-${r}@example.com" '{email:$v}')" ;;
      3) patch="$(jq -cn --arg v "0x${r}${r}" '{xid:$v}')" ;;
      4) patch="$(jq -cn --arg role "demo" --argjson n "$idx" '{meta:{role:$role,n:$n}}')" ;;
      *) die "unexpected idx=$idx" ;;
    esac
    update_user "$id" "$patch"
  done
  log "Updated: 5"
}

step_delete_10() {
  log "Deleting 10 users..."
  for idx in $(seq 5 14); do
    delete_user "${IDS[$idx]}"
  done
  log "Deleted: 10"
}

step_paging_checks() {
  log "Paging: size=5 (first page)"
  local body c
  body="$(get_users_page 0 5)"
  c="$(printf "%s" "$body" | jq -er '.users | length')"
  [[ "$c" -eq 5 ]] || die "expected 5 users, got $c"

  log "Paging: size=10 (first page)"
  body="$(get_users_page 0 10)"
  c="$(printf "%s" "$body" | jq -er '.users | length')"
  [[ "$c" -eq 10 ]] || die "expected 10 users, got $c"

  log "Paging: size=10 (second page, from=10)"
  body="$(get_users_page 10 10)"
  c="$(printf "%s" "$body" | jq -er '.users | length')"
  [[ "$c" -eq 10 ]] || die "expected 10 users on second page, got $c"
}

step_delete_all_remaining() {
  log "Deleting all remaining users..."
  local body ids deleted=0
  body="$(get_all_users)"
  ids="$(printf "%s" "$body" | ids_from_users || true)"
  while IFS= read -r id; do
    [[ -z "$id" ]] && continue
    delete_user "$id"
    deleted=$((deleted + 1))
  done <<<"$ids"
  log "Deleted remaining: $deleted"

  # verify empty
  body="$(get_all_users)"
  printf "%s" "$body" | jq -er '.users | length == 0' >/dev/null || die "expected empty after delete-all"
}

main() {
  log "SERVICE_URI=${SERVICE_URI}"
  if [[ -n "${ACCESS_TOKEN}" ]]; then
    log "ACCESS_TOKEN=present"
  else
    log "ACCESS_TOKEN=missing (no Authorization header will be sent)"
  fi

  step_create_100
  step_update_5
  step_delete_10
  step_paging_checks
  step_delete_all_remaining
  log "Done."
}

declare -a IDS=()
main "$@"
