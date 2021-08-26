#!/bin/bash
# SK file will be cached in memory, so access should be pretty fast

RP_HOST=${1:-rp-1}
RP_PORT=${2:-30002}

export SK_FILE=${3:-$SK_FILE}

while read data
do
   #echo "raw: $data"
   ts=`echo $(($(date +%s%N)/1000000))`
   sig=`echo $data | ./sig-data-sign.sh | ./sig-data-hex.sh`
   echo "$data $ts $sig"
done < <(nc -q -1 $RP_HOST $RP_PORT)