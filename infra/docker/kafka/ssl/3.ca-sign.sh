#!/bin/bash

keytool \
  -certreq \
  -keystore broker.jks \
  -alias localhost \
  -file broker-unsigned.crt \
  -storepass abcd1234

openssl x509 \
  -req \
  -CA ca.crt \
  -CAkey ca.key \
  -in broker-unsigned.crt \
  -out broker.crt \
  -days 365 \
  -CAcreateserial \
  -passin pass:abcd1234

#openssl x509 -text -noout -in broker.crt
