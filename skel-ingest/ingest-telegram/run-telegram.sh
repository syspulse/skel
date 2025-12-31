#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

# Run TDLight test application
#
# Usage:
#   export TELEGRAM_API_ID="12345678"
#   export TELEGRAM_API_HASH="0123456789abcdef0123456789abcdef"
#   export TELEGRAM_PHONE="+1234567890"
#   ./run-telegram.sh

# Optional: Set these to avoid interactive prompts
# export TELEGRAM_CODE="12345"       # 5-digit code from Telegram app
# export TELEGRAM_PASSWORD="secret"  # If 2FA is enabled

# Load environment if env.telegram exists
if [ -f "$(dirname $0)/env.telegram" ]; then
  source "$(dirname $0)/env.telegram"
  echo "Loaded env.telegram"
fi

# Session file location (will be created on first run)
export TELEGRAM_SESSION="${TELEGRAM_SESSION:-./tdlight-session}"

echo "Running TDLight test..."
echo ""
echo "NOTE: Bloop doesn't support stdin properly for interactive input."
echo "If authentication prompts don't work, use: ./run-telegram-sbt.sh"
echo ""

# Run with bloop
cd $CWD/../../
bloop run ingest_telegram --main io.syspulse.skel.telegram.App
