#!/bin/bash
# --protocol=tcp is needed if connecting to localhost (127.0.0.1 works fine)

mysql --host 127.0.0.1 --port 3306 -u root -proot --protocol=tcp 
