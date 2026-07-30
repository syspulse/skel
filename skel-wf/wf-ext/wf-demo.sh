#!/bin/bash
#
# wf-demo.sh - create 10 different Workflow assemblies against a running wf-ext server.
#
# Each assembly is built from an Assembly DSL pipeline posted to /config/dsl, which creates:
#   - DetectorSchema objects (one per distinct detector NAME in the pipeline)
#   - DetectorConfig objects  (one per `Detector` NODE)
#   - a WorkflowSchema (template) + WorkflowConfig (runtime instance) + WorkflowGrafs
#
# Naming conventions (no spaces in `name` fields):
#   WorkflowSchema / WorkflowConfig name : Workflow{Name}   e.g. WorkflowSingle
#   DetectorSchema   / DetectorConfig name : Detector{Name}  e.g. DetectorScanner
#   title (user-defined)                   : {name} User {n} e.g. WorkflowSingle User 1
#
# The 10 assemblies vary in number of schemas, detectors (configs) and links. Several of
# them reuse the SAME detector name on multiple nodes - demonstrating that several
# DetectorConfigs can be instances of the SAME DetectorSchema (1 schema -> many configs),
# each a distinct config (distinct id) that can later be configured differently.
#
# Start the server first, e.g.:   ./run-wf.sh server          (defaults to mem://)
#                          or:    ./run-wf.sh -d dir://store server
#
# Usage:  ./wf-demo.sh [N]
#   N  - optional limit on how many WorkflowConfigs (assemblies) to generate.
#        Each assembly produces exactly one WorkflowConfig. Default: all of them.
#        May also be given via the LIMIT env var. Examples:
#          ./wf-demo.sh 3       # only the first 3 configs
#          LIMIT=5 ./wf-demo.sh # only the first 5 configs

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
  "WorkflowSingle|Detector.DetectorScanner"                                                              # 1 schema, 1 config, 0 links
  "WorkflowPair|Detector.DetectorIngest -> Detector.DetectorAlert"                                     # 2 schemas, 2 configs, 1 link
  "WorkflowLinear3|Detector.DetectorCollect -> Detector.DetectorAnalyze -> Detector.DetectorReport"     # 3 schemas, 3 configs, 2 links
  "WorkflowTwin|Detector.DetectorProbe -> Detector.DetectorProbe"                                        # 1 schema, 2 configs (same schema!), 1 link
  "WorkflowFanline|Detector.DetectorScan -> Detector.DetectorScan -> Detector.DetectorReport"            # 2 schemas, 3 configs, 2 links
  "WorkflowMixed|Schema.DetectorTemplate -> Detector.DetectorWorker -> Detector.DetectorWorker"        # 2 schemas, 2 configs, 2 links
  "WorkflowPipe5|Detector.DetectorStage1 -> Detector.DetectorStage2 -> Detector.DetectorStage3 -> Detector.DetectorStage4 -> Detector.DetectorStage5" # 5 schemas, 5 configs, 4 links
  "WorkflowTriplet|Detector.DetectorGuard -> Detector.DetectorGuard -> Detector.DetectorGuard"           # 1 schema, 3 configs (same schema!), 2 links
  "WorkflowLinked|Detector.DetectorAlpha.0 -> 0.Detector.DetectorBeta.1 -> 1.Detector.DetectorGamma"     # 3 schemas, 3 configs, explicit link ids
  "WorkflowGateway|Detector.DetectorGateway -> Detector.DetectorFilter1 -> Detector.DetectorFilter2 -> Detector.DetectorGateway" # 3 schemas, 4 configs (gateway reused), 3 links
)

assemble() {
  local name="$1" pipeline="$2" user_num="$3" resp code body payload cid sid title
  payload="$(jq -cn --arg p "$pipeline" --arg n "$name" '{pipeline:$p,name:$n}')"
  resp="$(curl_api POST "/config/dsl" "$payload")"
  code="$(printf '%s' "$resp" | tail -n 1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  check "$code" "200" "$body" "POST /config/dsl ${name}"

  cid="$(printf '%s' "$body" | jq -r '.id')"
  sid="$(printf '%s' "$body" | jq -r '.sid')"
  [[ -n "$cid" && "$cid" != "null" ]] || die "POST /config/dsl ${name}: no config id" "$body"
  [[ -n "$sid" && "$sid" != "null" ]] || die "POST /config/dsl ${name}: no schema id" "$body"

  # title is user-defined (suffix " User N" distinguishes it from the system `name`)
  title="${name} User ${user_num}"
  payload="$(jq -cn --arg t "$title" '{title:$t}')"
  resp="$(curl_api PUT "/schema/${sid}" "$payload")"
  code="$(printf '%s' "$resp" | tail -n 1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  check "$code" "200" "$body" "PUT /schema/${sid} ${name}"

  resp="$(curl_api PUT "/config/${cid}" "$payload")"
  code="$(printf '%s' "$resp" | tail -n 1)"
  body="$(printf '%s' "$resp" | sed '$d')"
  check "$code" "200" "$body" "PUT /config/${cid} ${name}"

  # fetch the config with full detector+schema expansion to report what was built
  local full nodes links nconf nsch
  full="$(curl_api GET "/config/${cid}?entity=all")"
  full="$(printf '%s' "$full" | sed '$d')"
  nodes="$(printf '%s' "$full" | jq '.config.graph.nodes | length')"
  links="$(printf '%s' "$full" | jq '.config.graph.links | length')"
  nconf="$(printf '%s' "$full" | jq '(.config.graph.nodes | map(.cid) | map(select(. != null)) | length)')"
  nsch="$(printf '%s'  "$full" | jq '(.config.graph.nodes | map(.sid) | unique | length)')"

  printf '   %-16s cfg=%-3s schema=%-3s nodes=%-2s links=%-2s detectorConfigs=%-2s distinctSchemas=%-2s title=%-22s | %s\n' \
    "$name" "$cid" "$sid" "$nodes" "$links" "$nconf" "$nsch" "$title" "$pipeline"
}

# how many configs to generate: positional arg, else LIMIT env, else all
LIMIT=${1:-${LIMIT:-0}}
TOTAL=${#DEMO_ASSEMBLIES[@]}
if [[ "$LIMIT" =~ ^[0-9]+$ ]] && [[ "$LIMIT" -gt 0 ]] && [[ "$LIMIT" -lt "$TOTAL" ]]; then
  COUNT=$LIMIT
else
  COUNT=$TOTAL
fi

log "SERVICE_URI=${SERVICE_URI}"
log "Creating ${COUNT} of ${TOTAL} workflow assemblies (configs)..."
user_num=0
for entry in "${DEMO_ASSEMBLIES[@]:0:COUNT}"; do
  user_num=$((user_num + 1))
  IFS='|' read -r name pipeline <<<"$entry"
  assemble "$name" "$pipeline" "$user_num"
done

# summary
total="$(curl_api GET "/config" | sed '$d' | jq -r '.total')"
schemas="$(curl_api GET "/schema" | sed '$d' | jq -r '.total')"
grafs="$(curl_api GET "/graf" | sed '$d' | jq -r '.total')"
log "Done. WorkflowConfigs=${total}, WorkflowSchemas=${schemas}, WorkflowGrafs=${grafs}"
log "Tip: inspect shared schemas, e.g.  DETECTOR=full ./wf-config-get.sh 3"
