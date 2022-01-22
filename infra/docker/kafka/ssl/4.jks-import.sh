#!/bin/bash

keytool \
  -import \
  -file ca.crt \
  -keystore broker.jks \
  -alias ca \
  -storepass abcd1234 \
  -noprompt

keytool \
  -import \
  -file broker.crt \
  -keystore broker.jks \
  -alias localhost \
  -storepass abcd1234 \
  -noprompt

keytool -list -v -keystore broker.jks -storepass abcd1234

