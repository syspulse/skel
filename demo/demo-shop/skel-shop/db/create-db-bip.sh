#!/bin/bash

HOST=bip-mysql-1.isotope.isdev.info
PORT=3306
ROOT_USER=root
ROOT_PASS=309d5a3c5987f77fc5952f15b20a1818161df57c


mysql --host $HOST --port $PORT -u $ROOT_USER -p$ROOT_PASS < create-db.sql
