
PIPE=${1:-pipe-1}

rm -rf $PIPE
mkfifo $PIPE

