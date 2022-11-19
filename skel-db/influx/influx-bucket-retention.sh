#!/bin/bash

source influx-${SITE}.cred

BUCKET=${1:-$INFLUX_BUCKET}

influx bucket update -i $BUCKET -r 180d
