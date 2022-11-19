#!/bin/bash
DDL_FILE=${1:-medar_schema.sql}

sql2dbml "$DDL_FILE" --mysql