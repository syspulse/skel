#!/bin/bash

echo "JAVA_HOME=$JAVA_HOME"

# –ext san=dns:servername.domain.com \
# –ext san=ip:192.168.1.100
keytool \
  -genkey \
  -keystore broker.jks \
  -alias localhost \
  -dname CN=localhost \
  -keyalg RSA \
  -validity 365 \
  -ext san=dns:localhost \
  -storepass abcd1234

keytool -list -v -keystore broker.jks -storepass abcd1234
