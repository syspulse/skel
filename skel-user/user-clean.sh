#!/bin/bash
# Delete all users (pages through the list until empty).

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/user}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null || true`}
PAGE_SIZE=${PAGE_SIZE:-100}

die() {
  echo "ERROR: $1" >&2
  [[ -n "${2:-}" ]] && echo "$2" >&2
  exit 1
}

log() { echo "==> $*"; }

curl_api() {
  local method="$1" path="$2"
  local url="${SERVICE_URI%/}${path}"
  local -a h=(-H 'Content-Type: application/json')
  [[ -n "${ACCESS_TOKEN:-}" ]] && h+=(-H "Authorization: Bearer ${ACCESS_TOKEN}")
  curl -sS -X "$method" "${h[@]}" "$url" -w $'\n%{http_code}\n'
}

deleted=0
log "SERVICE_URI=${SERVICE_URI}"

while true; do
  resp="$(curl_api GET "/?from=0&size=${PAGE_SIZE}")"
  code="$(printf '%s' "$resp" | tail -n 1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  [[ "$code" == "200" ]] || die "GET /?from=0&size=${PAGE_SIZE} HTTP ${code}" "$body"

  mapfile -t ids < <(printf '%s' "$body" | jq -r '.users[]?.id // empty')
  [[ "${#ids[@]}" -eq 0 ]] && break

  for id in "${ids[@]}"; do
    [[ -z "$id" ]] && continue
    resp="$(curl_api DELETE "/${id}")"
    code="$(printf '%s' "$resp" | tail -n 1)"
    body="$(printf '%s' "$resp" | sed '$d')"
    case "$code" in
      200|204|404) deleted=$((deleted + 1)) ;;
      *) die "DELETE /${id} HTTP ${code}" "$body" ;;
    esac
  done
  log "deleted batch of ${#ids[@]} (total: ${deleted})"
done

log "Done. Deleted ${deleted} user(s)."
