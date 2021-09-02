#!/bin/bash

TABLE=${1:-Movies}

JAVA_OPTS=-Xmx2G /opt/amm/amm-2.12-2.2.0 ddb-scan.sc --table $TABLE --limit 0 --output /mnt/share/data/tmdb/content