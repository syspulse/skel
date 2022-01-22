#!/bin/bash

keytool \
  -import \
  -file ca.crt \
  -keystore broker.truststore \
  -alias ca \
  -storepass abcd1234 \
  -noprompt

keytool -list -v -keystore broker.truststore -storepass abcd1234