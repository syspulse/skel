#!/bin/bash

CONTRACT=${1:-i0x1f9840a85d5af5bf1d1762f925bdaddc4201f984}

curl -i "https://api.etherscan.io/api?module=contract&action=getabi&address=$CONTRACT&format=raw&apikey=$API_KEY"
