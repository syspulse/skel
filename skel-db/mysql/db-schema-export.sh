#!/bin/bash
mysqldump -d -u medar_user -pmedar_pass -h 127.0.0.1 medar_db >medar_schema.sql