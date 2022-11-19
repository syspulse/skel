#!/bin/bash
#ADDR=${1:-0x0186c7E33411617c03bc5AaA68642fFC6c60Fc8b}

SK=${1:-0x1da6847600b0ee25e9ad9a52abbd786dd2502fa4005dd5af9310b7cc7a3b25db}

if [ "$2" != "" ]; then
   USER=$2
else
   USER=`cat /proc/sys/kernel/random/uuid`
fi

REDIRECT_URI=${REDIRECT_URI:-http://localhost:8080/api/v1/auth/eth/callback}
SERVICE_USER=${SERVICE_USER:-http://localhost:8080/api/v1/user}
#SERVICE_USER=${SERVICE_USER:-http://localhost:8081/api/v1/user}
#SERVICE_USER=${SERVICE_USER:-http://localhost:8080/api/v1/auth/user}

OUTPUT=`./auth-web3-metamask.js $SK 2>/dev/null`
ADDR=`echo $OUTPUT | awk '{print $1}'`
SIG=`echo $OUTPUT | awk '{print $2}'`
MSG64=`echo $OUTPUT | awk '{print $3}'`

echo "addr: ${ADDR}"
echo "sig: ${SIG}"
echo "msg64: ${MSG64}"

# add user
uid=

LOCATION=`curl -i -s "http://localhost:8080/api/v1/auth/eth/auth?msg=${MSG64}&sig=${SIG}&addr=${ADDR}&response_type=code&client_id=UNKNOWN&scope=profile&state=state&redirect_uri=${REDIRECT_URI}" | grep Location`
echo "LOCATION: $LOCATION"
REDIRECT_URI=`echo $LOCATION | awk -F' ' '{print $2}'`

REDIRECT_URI=${REDIRECT_URI//$'\015'}
echo -n $REDIRECT_URI >1.tmp
echo -ne "REDIRECT_URI=${REDIRECT_URI}\n"

rsp=`http GET "${REDIRECT_URI}"`
echo --- Auth Response:
echo $rsp >AUTH_RSP.json
cat AUTH_RSP.json | jq

TOKEN=`cat AUTH_RSP.json | jq -r .accesToken`
echo "TOKEN=$TOKEN"
echo $TOKEN >ACCESS_TOKEN

echo
echo "User(uid) is missing from response, start Enrollment"
