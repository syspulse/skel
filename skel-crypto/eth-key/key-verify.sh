#!/bin/bash

DATA_FILE=${1}
SIG_FILE=${2:-sig.tmp}

PK_FILE=${PK_FILE:-pk.pem}
HASH_FILE=hash.tmp

openssl dgst -sha256 -binary "$DATA_FILE" >$HASH_FILE

cat $SIG_FILE | xxd -p -c 1000

openssl pkeyutl -verify -in $HASH_FILE -sigfile $SIG_FILE -pubin -inkey $PK_FILE