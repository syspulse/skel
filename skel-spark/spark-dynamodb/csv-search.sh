#!/bin/bash
# Example: "Aliens,Avatar,Star Wars"
SEARCH=${1}
if [ "$SEARCH" != "" ]; then
   SARG=--search "${SEARCH}"
fi

JAVA_OPTS=-Xmx5G /opt/amm/amm-2.12-2.2.0 csv-read.sc --parallelism 8 --limit 0 --input /mnt/share/data/tmdb/content $SARG
