#!/bin/bash

/opt/kafka_2.13-2.8.1/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic test-1 --consumer.config ./client-ssl.properties