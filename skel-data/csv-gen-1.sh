#!/bin/bash
N=${1:-100}
L=`echo "$N * 4"|bc`
shuf -i 1-$L| paste -d ","  - - - -|awk -F"," 'BEGIN{c=0}{print c","$0;c++}'
