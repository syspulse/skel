#!/bin/bash
#
# wf-demo.sh - create 10 different Workflow assemblies against a running wf-ext server.
#
# Each assembly is built from an Assembly DSL pipeline posted to /config/dsl, which creates:
#   - DetectorSchema objects (one per distinct detector NAME in the pipeline)
#   - DetectorConfig objects  (one per `Detector` NODE)
#   - a WorkflowSchema (template) + WorkflowConfig (runtime instance) + WorkflowGrafs
#
# The 10 assemblies vary in number of schemas, detectors (configs) and links. Several of
# them reuse the SAME detector name on multiple nodes - demonstrating that several
# DetectorConfigs can be instances of the SAME DetectorSchema (1 schema -> many configs),
# each a distinct config (distinct id) that can later be configured differently.
#
# Start the server first, e.g.:   ./run-wf.sh server          (defaults to mem://)
#                          or:    ./run-wf.sh -d dir://store server
#
# Usage:  ./wf-demo.sh

set -u

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/wf/ext}
ACCESS_TOKEN=${ACCESS_TOKEN-`cat ACCESS_TOKEN 2>/dev/null || true`}

need() { command -v "$1" >/dev/null 2>&1 || { echo "missing dependency: $1" >&2; exit 1; }; }
need curl
need jq

die() { echo "ERROR: $1" >&2; [[ -n "${2:-}" ]] && echo "$2" >&2; exit 1; }
log() { echo "==> $*"; }

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

check() { [[ "$1" == "$2" ]] || die "${4}: HTTP ${1}, expected ${2}" "$3"; }

# name|pipeline  : 10 assemblies of increasing/varying complexity.
#   Repeated detector names in one pipeline => shared DetectorSchema, distinct DetectorConfigs.
readonly -a DEMO_ASSEMBLIES=(
  "wf-single|Detector.scanner"                                                     # 1 schema, 1 config, 0 links
  "wf-pair|Detector.ingest -> Detector.alert"                                      # 2 schemas, 2 configs, 1 link
  "wf-linear3|Detector.collect -> Detector.analyze -> Detector.report"             # 3 schemas, 3 configs, 2 links
  "wf-twin|Detector.probe -> Detector.probe"                                       # 1 schema, 2 configs (same schema!), 1 link
  "wf-fanline|Detector.scan -> Detector.scan -> Detector.report"                   # 2 schemas, 3 configs, 2 links
  "wf-mixed|Schema.template -> Detector.worker -> Detector.worker"                 # 2 schemas, 2 configs, 2 links
  "wf-pipe5|Detector.s1 -> Detector.s2 -> Detector.s3 -> Detector.s4 -> Detector.s5" # 5 schemas, 5 configs, 4 links
  "wf-triplet|Detector.guard -> Detector.guard -> Detector.guard"                  # 1 schema, 3 configs (same schema!), 2 links
  "wf-linked|Detector.a.0 -> 0.Detector.b.1 -> 1.Detector.c"                       # 3 schemas, 3 configs, explicit link ids
  "wf-gateway|Detector.gw -> Detector.f1 -> Detector.f2 -> Detector.gw"            # 3 schemas, 4 configs (gw reused), 3 links
)

assemble() {
  local name="$1" pipeline="$2" resp code body payload cid sid
  payload="$(jq -cn --arg p "$pipeline" --arg n "$name" '{pipeline:$p,name:$n}')"
  resp="$(curl_api POST "/config/dsl" "$payload")"
  code="$(printf '%s' "$resp" | tail -n 1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  check "$code" "200" "$body" "POST /config/dsl ${name}"

  cid="$(printf '%s' "$body" | jq -r '.id')"
  sid="$(printf '%s' "$body" | jq -r '.sid')"
  [[ -n "$cid" && "$cid" != "null" ]] || die "POST /config/dsl ${name}: no config id" "$body"

  # fetch the config with full detector expansion to report what was built
  local full nodes links nconf nsch
  full="$(curl_api GET "/config/${cid}?detector=full")"
  full="$(printf '%s' "$full" | sed '$d')"
  nodes="$(printf '%s' "$full" | jq '.config.graph.nodes | length')"
  links="$(printf '%s' "$full" | jq '.config.graph.links | length')"
  nconf="$(printf '%s' "$full" | jq '(.config.graph.nodes | map(.cid) | map(select(. != null)) | length)')"
  nsch="$(printf '%s'  "$full" | jq '(.config.graph.nodes | map(.sid) | unique | length)')"

  printf '   %-10s cfg=%-3s schema=%-3s nodes=%-2s links=%-2s detectorConfigs=%-2s distinctSchemas=%-2s | %s\n' \
    "$name" "$cid" "$sid" "$nodes" "$links" "$nconf" "$nsch" "$pipeline"
}

log "SERVICE_URI=${SERVICE_URI}"
log "Creating ${#DEMO_ASSEMBLIES[@]} workflow assemblies..."
for entry in "${DEMO_ASSEMBLIES[@]}"; do
  IFS='|' read -r name pipeline <<<"$entry"
  assemble "$name" "$pipeline"
done

# summary
total="$(curl_api GET "/config" | sed '$d' | jq -r '.total')"
schemas="$(curl_api GET "/schema" | sed '$d' | jq -r '.total')"
grafs="$(curl_api GET "/graf" | sed '$d' | jq -r '.total')"
log "Done. WorkflowConfigs=${total}, WorkflowSchemas=${schemas}, WorkflowGrafs=${grafs}"
log "Tip: inspect shared schemas, e.g.  DETECTOR=full ./wf-config-get.sh 3"
