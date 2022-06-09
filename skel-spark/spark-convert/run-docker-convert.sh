#!/bin/bash

# example how to pass JVM memory to Docker
export JAVA_OPTS="-Xmx8g -Xms8g"

#docker run --rm -v /mnt/share/data/spark:/data syspulse/spark-convert --env JAVA_OPTS --input /data/csv --output /data/parquet/
docker run --rm -v /mnt/share/data/spark:/data --env JAVA_OPTS syspulse/spark-convert $@
