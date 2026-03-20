#!/bin/bash

# Continue Workflow Run from WAITING step
#
# Usage: ./wf-run-continue.sh <run-id> <config-id>
#
# Arguments:
#   run-id     - Workflow Run ID (UUID)
#   config-id  - DetectorConfig ID (step number to continue from)
#
# Examples:
#   ./wf-run-continue.sh abc-def-123 3
#   ./wf-run-continue.sh 019d0cf4-da55-773e-9c9b-37d806de9e7d 3
#
# Description:
#   Sends a continue signal to a workflow that is in WAITING status.
#   This is used for workflows with WAIT steps (e.g., PoL step in PoR workflow)
#   that require manual confirmation before proceeding.

export CWD=`echo $(dirname $(readlink -f $0))`

# Configuration
API_HOST=${API_HOST:-localhost}
API_PORT=${API_PORT:-8080}
API_URI=${API_URI:-/api/v1/wf}

# Parse arguments
RUN_ID=$1
CONFIG_ID=$2

# Validate arguments
if [ -z "$RUN_ID" ] || [ -z "$CONFIG_ID" ]; then
  echo "Error: Missing required arguments"
  echo ""
  echo "Usage: $0 <run-id> <config-id>"
  echo ""
  echo "Arguments:"
  echo "  run-id     - Workflow Run ID (UUID)"
  echo "  config-id  - DetectorConfig ID (step number to continue from)"
  echo ""
  echo "Examples:"
  echo "  $0 abc-def-123 3"
  echo "  $0 019d0cf4-da55-773e-9c9b-37d806de9e7d 3"
  echo ""
  exit 1
fi

# Build API URL
API_URL="http://${API_HOST}:${API_PORT}${API_URI}/run/${RUN_ID}/${CONFIG_ID}"

echo "Continuing Workflow Run..."
echo "Run ID:    ${RUN_ID}"
echo "Config ID: ${CONFIG_ID}"
echo "API URL:   ${API_URL}"
echo ""

# Send PUT request to continue workflow
RESPONSE=$(curl -s -w "\n%{http_code}" -X PUT "${API_URL}")

# Extract HTTP status code
HTTP_CODE=$(echo "$RESPONSE" | tail -n 1)
BODY=$(echo "$RESPONSE" | sed '$d')

echo "Response:"
echo "$BODY" | jq '.' 2>/dev/null || echo "$BODY"
echo ""

# Check HTTP status
if [ "$HTTP_CODE" = "200" ]; then
  echo "Status: ✓ Workflow continued successfully (HTTP $HTTP_CODE)"

  # Extract success status from response
  SUCCESS=$(echo "$BODY" | jq -r '.success' 2>/dev/null)
  MESSAGE=$(echo "$BODY" | jq -r '.message' 2>/dev/null)

  if [ "$SUCCESS" = "true" ]; then
    echo "Message: $MESSAGE"
  elif [ "$SUCCESS" = "false" ]; then
    echo "Warning: Continue signal sent but workflow reported: $MESSAGE"
  fi

  exit 0
else
  echo "Status: ✗ Failed to continue workflow (HTTP $HTTP_CODE)"
  exit 1
fi
