#!/bin/bash

HOST=bip-mysql-1.isotope.isdev.info
PORT=3306
USER=bip_user
PASS=bip_pass
DB=bip_db


mysql --host $HOST --port $PORT -u $USER -p$PASS $DB
