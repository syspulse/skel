#!/bin/bash
F=${1:-file.csv}
L=${2:-10000}
split -l $L --numeric-suffixes --additional-suffix=".csv" "$F" "file-"
