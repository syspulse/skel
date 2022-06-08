#!/bin/bash

# For AWS:
# Credentials chain: https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html 
# S3 access is s3a://bucket/folder/

INPUT=${1:-./data/}
OUTPUT=${2:-./data/parquet}
shift
shift

#AMM=${AMM:-/opt/amm/amm-2.12-2.2.0}
AMM=${AMM:-/opt/amm/amm-2.13-2.4.0}
CODEC=${CODEC:-parquet}
PAR=${PAR:-1}
# batch to process (avoid memory hog)
BATCH=${BATCH:-100}

echo "input=${INPUT}"
echo "output=${OUTPUT}"

JAVA_OPTS=-Xmx2G ${AMM} csv-convert.sc --parallelism $PAR --batch ${BATCH} --codec $CODEC --input $INPUT --output $OUTPUT $@
