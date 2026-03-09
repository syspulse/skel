#!/bin/bash
# Test Temporal signal CLI command
# Usage: ./temporal-signal.sh <run_id> <signal_name> <data_json>
# Example: ./temporal-signal.sh abc123-def456 receivePolSignal '{"ts":1234567890,"liabilities":[],"signature":"0x","signatureType":"public_key","publicKey":"0x"}'

RUN_ID=${1}
SIGNAL_NAME=${2:-receivePolSignal}
DATA_JSON=${3:-'{}'}

if [ -z "$RUN_ID" ]; then
  echo "Usage: $0 <run_id> [signal_name] [data_json]"
  echo ""
  echo "Examples:"
  echo "  # Signal with PoL data"
  echo "  $0 abc123-def456 receivePolSignal '{\"ts\":1234567890,\"liabilities\":[],\"signature\":\"0x\",\"signatureType\":\"public_key\",\"publicKey\":\"0x\"}'"
  echo ""
  echo "  # Signal with minimal data"
  echo "  $0 abc123-def456 receivePolSignal '{}'"
  exit 1
fi

echo "Sending signal via Temporal CLI..."
echo "RUN_ID: $RUN_ID"
echo "SIGNAL: $SIGNAL_NAME"
echo "DATA: $DATA_JSON"
echo ""

./run-wf.sh temporal signal "$RUN_ID" "$SIGNAL_NAME" "$DATA_JSON"
