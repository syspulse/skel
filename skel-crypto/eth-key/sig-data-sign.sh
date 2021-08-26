#!/bin/bash

SK_FILE=${SK_FILE:-sk.pem}
PK_FILE=${PK_FILE:-pk.pem}

openssl dgst -sha3-256 -binary | openssl pkeyutl -sign -inkey $SK_FILE | openssl asn1parse -inform DER
#openssl dgst -sha3-256 -binary | openssl pkeyutl -sign -inkey $SK_FILE | xxd -p -c 1000
