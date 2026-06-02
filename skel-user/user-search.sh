#!/bin/bash
set -euo pipefail

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/user}
ACCESS_TOKEN=${ACCESS_TOKEN:-}
if [[ -z "${ACCESS_TOKEN}" && -f "ACCESS_TOKEN" ]]; then
  ACCESS_TOKEN="$(cat ACCESS_TOKEN)"
fi

SEARCH=${SEARCH:-${1:-}}
METHOD=${METHOD:-GET}
FROM=${FROM:-}
SIZE=${SIZE:-}

if [[ -z "${SEARCH}" ]]; then
  echo "usage: $0 <query>" >&2
  echo "  optional: METHOD=GET|POST (default GET), FROM=0 SIZE=10 (both required)" >&2
  exit 1
fi

if [[ -n "${FROM}" || -n "${SIZE}" ]]; then
  if [[ -z "${FROM}" || -z "${SIZE}" ]]; then
    echo "FROM and SIZE must both be set for paging (e.g. FROM=0 SIZE=10)" >&2
    exit 1
  fi
fi

AUTH=()
if [[ -n "${ACCESS_TOKEN}" ]]; then
  AUTH=(-H "Authorization: Bearer ${ACCESS_TOKEN}")
fi

case "${METHOD}" in
  GET|get)
  QUERY=(--data-urlencode "search=${SEARCH}")
  [[ -n "${FROM}" ]] && QUERY+=(--data-urlencode "from=${FROM}")
  [[ -n "${SIZE}" ]] && QUERY+=(--data-urlencode "size=${SIZE}")
  curl -S -s -D /dev/stderr -X GET \
    -H 'Content-Type: application/json' \
    "${AUTH[@]}" \
    --get "${QUERY[@]}" \
    "${SERVICE_URI}/"
  ;;
  POST|post)
  DATA_JSON="$(jq -cn \
    --arg search "${SEARCH}" \
    --arg from "${FROM}" \
    --arg size "${SIZE}" \
    '{search:$search}
     + (if $from != "" then {from: ($from|tonumber)} else {} end)
     + (if $size != "" then {size: ($size|tonumber)} else {} end)')"
  curl -S -s -D /dev/stderr -X POST \
    -H 'Content-Type: application/json' \
    "${AUTH[@]}" \
    --data "${DATA_JSON}" \
    "${SERVICE_URI}/search"
  ;;
  *)
  echo "unsupported METHOD=${METHOD}; use GET or POST" >&2
  exit 1
  ;;
esac
