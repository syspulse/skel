#!/bin/bash

RSP_FILE=${1:-rsp_OK.txt}
PORT=${PORT:-8100}

RSP=`cat $RSP_FILE`

echo "0.0.0.0:$PORT..."

#while true; do { echo -e 'HTTP/1.1 200 OK\r\n'; echo $RSP; } | nc -l $PORT -q 1; done
while true; do { echo -e 'HTTP/1.1 200 OK\r\n'; echo $RSP; } | nc -l $PORT -q 0; done
