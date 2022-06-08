#!/bin/bash
docker run --rm -v /mnt/share/data/spark:/data syspulse/spark-convert --input /data/csv --output /data/parquet/
