#!/bin/bash
./csv-gen-1.sh 20000000 >file.csv
./csv-split.sh file.csv 5000000
time amm ./csv-processor.sc --file file.csv
time amm ./csv-processor.sc --file file- 10
