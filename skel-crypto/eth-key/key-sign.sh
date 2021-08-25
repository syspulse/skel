#!/bin/bash

DATA_FILE=${1}

SK_FILE=${SK_FILE:-sk.pem}
PK_FILE=${PK_FILE:-pk.pem}
HASH_FILE=hash.tmp
SIG_FILE=sig.tmp

openssl dgst -sha256 -binary "$DATA_FILE" >$HASH_FILE
openssl pkeyutl -sign -inkey $SK_FILE -in $HASH_FILE >$SIG_FILE

cat $SIG_FILE | xxd -p -c 1000

openssl pkeyutl -verify -in $HASH_FILE -sigfile $SIG_FILE -pubin -inkey $PK_FILE