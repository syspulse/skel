#!/bin/bash

kafkacat -P -t test-1 -b localhost:9092 -X security.protocol=ssl -X ssl.ca.location=`pwd`/ca.crt