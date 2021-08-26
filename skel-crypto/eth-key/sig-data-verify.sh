#!/bin/bash

SK_FILE=${SK_FILE:-sk.pem}
PK_FILE=${PK_FILE:-pk.pem}
SIG_FILE=${SIG_FILE:-sig.tmp}

openssl dgst -sha3-256 -binary | openssl pkeyutl -verify -sigfile $SIG_FILE -pubin -inkey $PK_FILE
