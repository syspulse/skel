#!/bin/bash

# pad base64URL encoded to base64
paddit() {
  input=$1
  l=`echo -n $input | wc -c`
  while [ `expr $l % 4` -ne 0 ]
  do
    input="${input}="
    l=`echo -n $input | wc -c`
  done
  echo $input
}

# convert seconds to days, hours, minutes, seconds
format_time() {
    local seconds=$1
    local days=$((seconds / 86400))
    local hours=$(( (seconds % 86400) / 3600 ))
    local minutes=$(( (seconds % 3600) / 60 ))
    local secs=$((seconds % 60))
    
    local result=""
    [[ $days -gt 0 ]] && result="${days}d "
    [[ $hours -gt 0 ]] && result="${result}${hours}h "
    [[ $minutes -gt 0 ]] && result="${result}${minutes}m "
    result="${result}${secs}s"
    echo "$result"
}

# read and split the token and do some base64URL translation
read jwt
read h p s <<< $(echo $jwt | tr [-_] [+/] | sed 's/\./ /g')

h=`paddit $h`
p=`paddit $p`

# assuming we have jq installed
# echo $h | base64 -d | jq .
# echo $p | base64 -d | jq .

header=`echo $h | base64 -d`
payload=`echo $p | base64 -d`

echo $header | jq .
echo $payload | jq .

exp=`echo $payload | jq .exp`
now=`date +%s`
exp_local=`date -d @$exp "+%Y-%m-%d %H:%M:%S"`

echo "Expiration (UTC): $exp_local"
echo "Current time: `date "+%Y-%m-%d %H:%M:%S"`"

if [ $now -gt $exp ]; then
    echo "Token has EXPIRED"
    exit 1
else
    remaining=$((exp - now))
    remaining_formatted=$(format_time $remaining)
    echo "Token is VALID ($remaining_formatted remaining)"
    exit 0
fi


