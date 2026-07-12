#!/bin/bash
# Assemble a WorkflowConfig from a DSL pipeline and link it to an Engine runtime by xid.
# Runs the wf-ext App CLI (persists into the configured datastore).
#
#   ./wf-engine-link.sh <runtimeId> '[PoO] -> [PoR] -> [Report]'
#   DATASTORE=dir://store ./wf-engine-link.sh 019e7473-05d1-789f-bb4b-44845bd69fc6 '[PoO] -> [PoR] -> [Report]'
#
# The bracket shorthand [X] is rewritten to Detector.X; the classic
# 'Detector.a -> Detector.b' syntax is also accepted.
RUNTIME_ID=${1:?"usage: wf-engine-link.sh <runtimeId> '<pipeline>'"}
shift
PIPELINE="${*:?"usage: wf-engine-link.sh <runtimeId> '<pipeline>'"}"

DATASTORE=${DATASTORE:-mem://}
ENGINE=${ENGINE:-temporal://}

exec ./run-wf.sh --datastore="$DATASTORE" --engine="$ENGINE" assembly-link "$RUNTIME_ID" "$PIPELINE"
