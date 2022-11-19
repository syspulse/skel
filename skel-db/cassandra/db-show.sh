#!/bin/bash

TABLE=${1:-table_1}
SPACE=${2:-table_space_1}

./db-cqlsh.sh <<EOF
USE $SPACE;
SELECT * FROM $TABLE;
exit;
EOF
