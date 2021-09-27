#!/bin/bash
# --protocol=tcp is needed if connecting to localhost (127.0.0.1 works fine)

SQL_FILE=${1}

mysql --host 127.0.0.1 --port 3306 -u root -proot --protocol=tcp < ${SQL_FILE}
