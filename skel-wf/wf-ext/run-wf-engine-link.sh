#!/bin/bash
# Build a WorkflowConfig from a DSL pipeline referencing EXISTING DetectorConfigs by name (latest
# version) and link it to an Engine runtime by xid. Creates NO Detector* entities (a missing name
# fails). Runs the wf-ext App CLI (persists into the configured datastore).
#
#   ./run-wf-engine-link.sh <runtimeId> '[PoO] -> [PoR] -> [Report]'
#   DATASTORE=dir://store ./run-wf-engine-link.sh 019e7473-05d1-789f-bb4b-44845bd69fc6 '[PoO] -> [PoR] -> [Report]'
#
# The bracket shorthand [X] is rewritten to Detector.X; the classic
# 'Detector.a -> Detector.b' syntax is also accepted.
RUNTIME_ID=${1:?"usage: wf-engine-link.sh <runtimeId> '<pipeline>'"}
shift
PIPELINE="${*:?"usage: wf-engine-link.sh <runtimeId> '<pipeline>'"}"

DATASTORE=${DATASTORE:-mem://}
ENGINE=${ENGINE:-temporal://}

exec ./run-wf.sh --datastore="$DATASTORE" --engine="$ENGINE" link "$RUNTIME_ID" "$PIPELINE"
