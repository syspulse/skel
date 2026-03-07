#!/bin/bash

# Execute PoR Workflow
# Usage: ./run-por-flow.sh <flow> [cex-name]
# Examples:
#   ./run-por-flow.sh flow-1 Binance
#   ./run-por-flow.sh flow-2 Coinbase

export CWD=`echo $(dirname $(readlink -f $0))`

export SITE=${SITE:-temporal}
export TEMPORAL_SERVICE_ADDRESS=${TEMPORAL_SERVICE_ADDRESS:-127.0.0.1:7233}

FLOW=${1:-flow-1}
CEX_NAME=${2:-DefaultCEX}

echo "Executing PoR Workflow..."
echo "Flow: $FLOW"
echo "CEX Name: $CEX_NAME"
echo "Temporal Service: $TEMPORAL_SERVICE_ADDRESS"
echo ""

exec ${CWD}/run-temporal.sh por-start $FLOW $CEX_NAME
