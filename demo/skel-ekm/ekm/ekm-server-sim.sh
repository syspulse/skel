#!/bin/bash

PORT=${1:-30001}
RSP=${2:-rsp1.json}
TMP=/tmp/ekm-request.tmp

echo "RSP: ${RSP}"
echo "Listening: :${PORT}"

echo -e 'HTTP/1.1 200 OK\r\n' > $TMP
cat $RSP >> $TMP

while true; do nc -lp $PORT -q1 < ${TMP}; done
