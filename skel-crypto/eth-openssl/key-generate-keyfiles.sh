#!/bin/bash

SK_FILE=sk.pem
PK_FILE=pk.pem

openssl ecparam -name secp256k1 -genkey -noout -out ${SK_FILE}.tmp
openssl ec -in ${SK_FILE}.tmp -pubout > ${PK_FILE}

# convert to Java compatible file format
openssl pkcs8 -topk8 -nocrypt -in ${SK_FILE}.tmp -out $SK_FILE

cat $SK_FILE
echo 
cat $PK_FILE
echo 
openssl ec -in $SK_FILE -text -noout
