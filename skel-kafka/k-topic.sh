#!/bin/bash
CWD=`echo $(dirname $(readlink -f $0))`

#../tools/kafkacat -b bip-demo.isotope.isdev.info:9092 -r bip-demo.isotope.isdev.info:8081 -s avro -C -t bip_kinesis_kustomer -o beginning -c 0
TOPIC=${1:-blocks}
BROKER=${BROKER:-172.17.0.1:9092}

which kcat

if [ "$?" == "0" ]; then
   KCAT=kcat
else
   KCAT="$CWD/kcat"
fi

$KCAT -b $BROKER -C -t $TOPIC -o beginning -c 0
