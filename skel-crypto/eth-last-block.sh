#!/bin/bash

ETH_RPC_URL=${ETH_RPC_URL:-http://localhost:8545}

latest=`curl -s POST --data '{"jsonrpc":"2.0","method":"eth_getBlockByNumber","params":["latest", false],"id":1}' -H "Content-Type: application/json" ${ETH_RPC_URL} |jq -r .result.number | xargs printf "%d"`

echo $latest
printf "0x%x\n" $latest
