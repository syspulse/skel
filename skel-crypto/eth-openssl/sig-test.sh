#!/bin/bash
# Test openssl ECDSA integration 
while [ 1 ]; do 
  m=`date +%N | sha256sum | base64 | head -1`
  output=`echo -n "$m" | ./sig-data-sign.sh | paste - - -|awk '{print $13,$20}'|awk -F':' '{print $2,$3}'`
  echo "$m $output"
  echo "$m $output" | ammonite ./pem-fuzzy.sc
  sleep 1
done
