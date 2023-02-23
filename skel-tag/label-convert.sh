#!/bin/bash
# Convert to skel-label compatible CSV

cat $1 | awk -F"," '{s= ""; for (i=6;i<=NF;i++) s = s$i";"; split($3,a,"[/:. ]"); ts = mktime(a[0]" "a[1]" "a[2]" "a[3]" "a[4]" "a[5]" "a[6]); print $4","ts","$5","s}'

