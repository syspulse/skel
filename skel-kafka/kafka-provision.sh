#!/bin/bash

BROKER=${1:-localhost:9092}

kafka-topics.sh --bootstrap-server $BROKER --create --topic eth.blocks
