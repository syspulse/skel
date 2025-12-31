#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

# Run TDLight with sbt (supports stdin properly)

if [ -f "$(dirname $0)/env.telegram" ]; then
  source "$(dirname $0)/env.telegram"
  echo "Loaded env.telegram"
fi

export TELEGRAM_SESSION="${TELEGRAM_SESSION:-$CWD/tdlight-session}"

echo "Running with sbt (supports interactive input)..."
echo ""

cd $CWD/../../
sbt "project ingest_telegram" "runMain io.syspulse.skel.telegram.App"
