#!/bin/bash

ETH_RPC=http://192.168.1.13:8545

START_BLOCK=${1:-14747950}

if [ "$START_BLOCK" != "latest" ]; then
  rm -f last_synced_block.txt
  START_BLOCK_ARG="--start-block $START_BLOCK"
else
  START_BLOCK_ARG=""
fi


ethereumetl stream -e transaction $START_BLOCK_ARG --provider-uri $ETH_RPC 2>/dev/null
