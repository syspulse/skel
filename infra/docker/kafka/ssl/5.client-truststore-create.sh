#!/bin/bash

keytool \
  -import \
  -file ca.crt \
  -keystore client.truststore \
  -alias ca \
  -storepass abcd1234 \
  -noprompt

keytool -list -v -keystore client.truststore -storepass abcd1234
