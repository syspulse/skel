#!/bin/bash
ADDR=${1:-0x0186c7E33411617c03bc5AaA68642fFC6c60Fc8b}
SIG=${2:-0xd154fd4171820e35a1cf48e67242779714d176e59e19de02dcf62b78cd75946d0bd46da493810b66b589667286d05c0f4e1b0cc6f29a544361ad639b0a6614041c}
REDIRECT_URI=${3:-http://localhost:8080/api/v1/auth/eth/callback}

curl -i "http://localhost:8080/api/v1/auth/eth/auth?sig=${SIG}&addr=${ADDR}&response_type=code&client_id=UNKNOWN&scope=profile&state=state&redirect_uri=${REDIRECT_URI}"
