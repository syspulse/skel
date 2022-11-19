#!/bin/bash
openssl req \
  -new \
  -x509 \
  -days 365 \
  -keyout ca.key \
  -out ca.crt \
  -subj "/C=SO/L=Earth/CN=Certificate Authority" \
  -passout pass:abcd1234

openssl x509 -text -noout -in ca.crt
