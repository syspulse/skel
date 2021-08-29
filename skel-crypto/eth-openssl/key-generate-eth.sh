#!/bin/bash
# Based on https://gist.github.com/miguelmota/3793b160992b4ea0b616497b8e5aee2f
# All credits to github.com/miguelmota
# Changed keccak-256sum dependency to openssl

TMP_KEY=tmp.key
SK_FILE=sk.pem
PK_FILE=pk.pem

openssl ecparam -name secp256k1 -genkey -noout | openssl ec -text -noout >$TMP_KEY

# Extract the public key and remove the EC prefix 0x04
PK=`cat $TMP_KEY | grep pub -A 5 | tail -n +2 | tr -d '\n[:space:]:' | sed 's/^04//'` 

# Extract the private key and remove the leading zero byte
SK=`cat $TMP_KEY | grep priv -A 3 | tail -n +2 | tr -d '\n[:space:]:' | sed 's/^00//'` 

# Generate the hash and take the address part
# WARNING: openssl sha3-256 is NOT compatible with Keccak256 !!!
A=`echo $PK | keccak -h | tr -d ' -' | tail -c 41` 

echo 0x$SK 
echo 0x$PK 
echo 0x$A

#rm $TMP_KEY
