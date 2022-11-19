#!/bin/bash

# connect without certificate verification
kafkacat -L -b localhost:9092 -X security.protocol=ssl -X enable.ssl.certificate.verification=false

# connect with certificate verification
kafkacat -L -b localhost:9092 -X security.protocol=ssl -X ssl.ca.location=`pwd`/ca.crt

# connect with cert verification by CA
# For manual verification: SSL connection returns broker.crt
openssl s_client -connect localhost:9092 -CAfile=`pwd`/ca.crt <<< "Q"

