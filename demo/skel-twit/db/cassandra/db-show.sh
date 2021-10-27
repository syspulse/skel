#!/bin/bash
./db-cqlsh.sh <<EOF
USE twit_space;
SELECT * FROM twit;
exit;
EOF
