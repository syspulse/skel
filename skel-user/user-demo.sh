#!/bin/bash

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/user}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null || true`}

need() { command -v "$1" >/dev/null 2>&1 || { echo "missing dependency: $1" >&2; exit 1; }; }
need curl
need jq
need hexdump

die() {
  echo "ERROR: $1" >&2
  [[ -n "${2:-}" ]] && echo "$2" >&2
  exit 1
}

log() { echo "==> $*"; }

rand_hex8() { hexdump -n 4 -v -e '/1 "%02x"' /dev/urandom; }

# EN/DE: given|family   JA: family|given|romaji
readonly -a DEMO_NAMES=(
  "John|Smith" "Jane|Doe" "Michael|Brown" "Emily|Davis" "David|Wilson"
  "Sarah|Johnson" "Robert|Taylor" "Laura|Anderson" "James|Thomas" "Anna|Martinez"
  "William|Clark" "Olivia|Lewis" "Richard|Walker" "Sophia|Hall" "Charles|Allen"
  "Hans|Müller" "Anna|Schmidt" "Peter|Schneider" "Julia|Fischer" "Thomas|Weber"
  "Klaus|Wagner" "Sabine|Becker" "Stefan|Hoffmann" "Monika|Schäfer" "Andreas|Koch"
  "Claudia|Bauer" "Markus|Richter" "Petra|Klein" "Frank|Wolf" "Heike|Neumann"
  "田中|由紀|yuki" "鈴木|陽翔|haruto" "高橋|さくら|sakura" "渡辺|蓮|ren" "伊藤|花|hana"
  "山本|颯太|sota" "中村|葵|aoi" "小林|海斗|kaito" "加藤|芽衣|mei" "吉田|陸|riku"
  "Emma|Harris" "Daniel|Martin" "Grace|Thompson" "Paul|Garcia" "Lisa|Robinson"
  "Felix|Zimmermann" "Lena|Hartmann" "Jonas|Kruger" "Nina|Schulz" "Lukas|Braun"
  "佐藤|健太|kenta" "松本|美咲|misaki" "井上|翔|sho" "木村|結衣|yui" "林|一郎|ichiro"
)

readonly -a DEMO_DOMAINS=(
  exampla.com domain.org gmail.com example.com outlook.com
  yahoo.com company.io mail.test hotmail.com proton.me
)

email_prefix() {
  local first="$1"
  first="${first,,}"
  first="${first//ä/ae}"; first="${first//ö/oe}"; first="${first//ü/ue}"; first="${first//ß/ss}"
  printf '%s' "$first" | tr -cd '[:alnum:]'
}

pick_name() { printf '%s' "${DEMO_NAMES[RANDOM % ${#DEMO_NAMES[@]}]}"; }
pick_domain() { printf '%s' "${DEMO_DOMAINS[RANDOM % ${#DEMO_DOMAINS[@]}]}"; }

assign_demo_identity() {
  local pair="$1" suffix="${2:-}" a b c prefix domain
  IFS='|' read -r a b c <<<"$pair"
  if [[ -n "$c" ]]; then
    DEMO_FULL_NAME="${a} ${b}"
    prefix="${c,,}"
  else
    DEMO_FULL_NAME="${a} ${b}"
    prefix="$(email_prefix "$a")"
  fi
  [[ -n "$suffix" ]] && prefix="${prefix}${suffix}"
  DEMO_EMAIL="${prefix}@$(pick_domain)"
}

curl_api() {
  local method="$1" path="$2" body="${3:-}"
  local url="${SERVICE_URI%/}${path}"
  local -a h=(-H 'Content-Type: application/json')
  [[ -n "${ACCESS_TOKEN:-}" ]] && h+=(-H "Authorization: Bearer ${ACCESS_TOKEN}")
  if [[ -n "$body" ]]; then
    curl -sS -X "$method" "${h[@]}" --data "$body" "$url" -w $'\n%{http_code}\n'
  else
    curl -sS -X "$method" "${h[@]}" "$url" -w $'\n%{http_code}\n'
  fi
}

check() {
  local got="$1" want="$2" body="$3" msg="$4"
  [[ "$got" == "$want" ]] || die "${msg}: HTTP ${got}, expected ${want}" "$body"
}

create_user() {
  local email="$1" name="${2:-}" resp code body id payload
  if [[ -n "$name" ]]; then
    payload="$(jq -cn --arg email "$email" --arg name "$name" '{email:$email,name:$name}')"
  else
    payload="$(jq -cn --arg email "$email" '{email:$email}')"
  fi
  resp="$(curl_api POST "/" "$payload")"
  code="$(printf '%s' "$resp" | tail -n 1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  check "$code" "201" "$body" "POST / ${email}"
  id="$(printf '%s' "$body" | jq -r '.id')"
  [[ -n "$id" && "$id" != "null" ]] || die "POST / ${email}: no id" "$body"
  printf '%s' "$id"
}

update_user() {
  local id="$1" patch="$2" resp code body
  resp="$(curl_api PUT "/${id}" "$patch")"
  code="$(printf '%s' "$resp" | tail -n 1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  check "$code" "200" "$body" "PUT /${id}"
}

delete_user() {
  local id="$1" resp code body
  resp="$(curl_api DELETE "/${id}")"
  code="$(printf '%s' "$resp" | tail -n 1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  case "$code" in
    200|204|404) ;;
    *) die "DELETE /${id}: HTTP ${code}" "$body" ;;
  esac
}

get_users_page() {
  local from="$1" size="$2" resp code body
  resp="$(curl_api GET "/?from=${from}&size=${size}")"
  code="$(printf '%s' "$resp" | tail -n 1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  check "$code" "200" "$body" "GET /?from=${from}&size=${size}"
  printf '%s' "$body"
}

get_all_users() {
  local resp code body
  resp="$(curl_api GET "/")"
  code="$(printf '%s' "$resp" | tail -n 1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  check "$code" "200" "$body" "GET /"
  printf '%s' "$body"
}

step_create_100() {
  log "Creating 100 users..."
  IDS=()
  declare -A USED_EMAILS=()
  for _ in $(seq 1 100); do
    local pair suffix= n=2 id
    pair="$(pick_name)"
    assign_demo_identity "$pair"
    while [[ -n "${USED_EMAILS[$DEMO_EMAIL]:-}" ]]; do
      assign_demo_identity "$pair" "$n"
      n=$((n + 1))
    done
    USED_EMAILS["$DEMO_EMAIL"]=1
    id="$(create_user "$DEMO_EMAIL" "$DEMO_FULL_NAME")"
    IDS+=("$id")
  done
  log "Created: ${#IDS[@]}"
}

step_update_5() {
  log "Updating 5 users..."
  for idx in $(seq 0 4); do
    local id r patch
    id="${IDS[$idx]}"
    r="$(rand_hex8)"
    case "$idx" in
      0) patch="$(jq -cn --arg v "Name-${r}" '{name:$v}')" ;;
      1) patch="$(jq -cn --arg v "https://example.com/icon/${r}.png" '{avatar:$v}')" ;;
      2) assign_demo_identity "$(pick_name)"; patch="$(jq -cn --arg v "$DEMO_EMAIL" '{email:$v}')" ;;
      3) patch="$(jq -cn --arg v "0x${r}${r}" '{xid:$v}')" ;;
      4) patch="$(jq -cn --arg role "demo" --argjson n "$idx" '{meta:{role:$role,n:$n}}')" ;;
    esac
    update_user "$id" "$patch"
  done
}

step_delete_10() {
  log "Deleting 10 users..."
  for idx in $(seq 5 14); do delete_user "${IDS[$idx]}"; done
}

step_paging_checks() {
  local body c
  log "Paging: from=0 size=5"
  body="$(get_users_page 0 5)"
  c="$(printf '%s' "$body" | jq '.users | length')"
  [[ "$c" -eq 5 ]] || die "expected 5 users, got ${c}" "$body"

  log "Paging: from=0 size=10"
  body="$(get_users_page 0 10)"
  c="$(printf '%s' "$body" | jq '.users | length')"
  [[ "$c" -eq 10 ]] || die "expected 10 users, got ${c}" "$body"

  log "Paging: from=10 size=10"
  body="$(get_users_page 10 10)"
  c="$(printf '%s' "$body" | jq '.users | length')"
  [[ "$c" -eq 10 ]] || die "expected 10 on page 2, got ${c}" "$body"
}

step_delete_all_remaining() {
  log "Deleting remaining users..."
  local body id deleted=0
  body="$(get_all_users)"
  while IFS= read -r id; do
    [[ -z "$id" ]] && continue
    delete_user "$id"
    deleted=$((deleted + 1))
  done < <(printf '%s' "$body" | jq -r '.users[]?.id')
  log "Deleted: ${deleted}"
  body="$(get_all_users)"
  c="$(printf '%s' "$body" | jq '.users | length')"
  [[ "$c" -eq 0 ]] || die "expected 0 users left, got ${c}" "$body"
}

declare -a IDS=()
log "SERVICE_URI=${SERVICE_URI}"
step_create_100
step_update_5
step_delete_10
step_paging_checks
# step_delete_all_remaining
log "Done."
