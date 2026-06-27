LOCAL=${1:-8545}
REMOTE=${2:-localhost:8546}

socat -v -d -d TCP-LISTEN:$LOCAL,fork TCP:$REMOTE
