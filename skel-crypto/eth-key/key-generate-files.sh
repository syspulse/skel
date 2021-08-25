#!/bin/bash

SK_FILE=sk.pem
PK_FILE=pk.pem

openssl ecparam -name secp256k1 -genkey -noout -out $SK_FILE
openssl ec -in $SK_FILE -pubout > $PK_FILE

cat $SK_FILE
echo 
cat $PK_FILE
