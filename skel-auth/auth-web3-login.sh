#!/bin/bash
#ADDR=${1:-0x0186c7E33411617c03bc5AaA68642fFC6c60Fc8b}

SK=${1:-0x1da6847600b0ee25e9ad9a52abbd786dd2502fa4005dd5af9310b7cc7a3b25db}
CLIENT_ID=${ETH_AUTH_CLIENT_ID:-CLIENT_ID-00001}
STATE=${STATE:-1111111111}

if [ "$2" != "" ]; then
   USER=$2
else
   USER=`cat /proc/sys/kernel/random/uuid`
fi

AUTH_URI=${AUTH_URI:-http://localhost:8080/api/v1/auth}
REDIRECT_URI=${AUTH_URI}/eth/callback

#USER_URI=${USER_URI:-http://localhost:8080/api/v1/user}
#USER_URI=${USER_URI:-http://localhost:8080/api/v1/auth/user}

USER_URI=${USER_URI:-http://localhost:8081/api/v1/user}
ENROLL_URI=${ENROLL_URI:-http://localhost:8083/api/v1/enroll}

>&2 echo "=================================== Login to Metamask (${SK})"

OUTPUT=`./auth-web3-metamask.js $SK 2>/dev/null`
ADDR=`echo $OUTPUT | awk '{print $1}'`
SIG=`echo $OUTPUT | awk '{print $2}'`
MSG64=`echo $OUTPUT | awk '{print $3}'`

>&2 echo "addr: ${ADDR}"
>&2 echo "sig: ${SIG}"
>&2 echo "msg64: ${MSG64}"

>&2 echo "=================================== OAuth2 Code -> ${AUTH_URI}"

RSP=`curl -i -s "$AUTH_URI/eth/auth?msg=${MSG64}&sig=${SIG}&addr=${ADDR}&response_type=code&client_id=${CLIENT_ID}&scope=profile&state=${STATE}&redirect_uri=${REDIRECT_URI}"`
>&2 echo "RSP: $RSP"
LOCATION=`echo "$RSP" | grep -i Location`
>&2 echo "LOCATION: $LOCATION"
REDIRECT_URI=`echo $LOCATION | awk -F' ' '{print $2}'`

REDIRECT_URI=${REDIRECT_URI//$'\015'}
echo -n $REDIRECT_URI >1.tmp
echo -ne "REDIRECT_URI=${REDIRECT_URI}\n"

>&2 echo "=================================== OAuth2 Redirecting -> ${REDIRECT_URI}"

rsp=`http GET "${REDIRECT_URI}"`
echo $rsp >AUTH_RSP.json
cat AUTH_RSP.json | jq

>&2 echo "=================================== Saving JWT"
ACCESS_TOKEN=`cat AUTH_RSP.json | jq -r .accessToken`
echo "ACCESS_TOKEN=$ACCESS_TOKEN"
echo $ACCESS_TOKEN >ACCESS_TOKEN

xid=`cat AUTH_RSP.json | jq -r .xid`
uid=`cat AUTH_RSP.json | jq -r .uid`

echo "xid: $xid"
echo "uid: $uid"

if [ "$uid" == "null" ]; then
   echo "User does not exist -> Enrolling to ${ENROLL_URI}"

   R=`SERVICE_URI=$ENROLL_URI ../skel-enroll/enroll-start.sh ${xid:0:8}@domain.eth ${xid:0:4} ${xid}`
   eid=`echo $R | jq -r .id`
   echo "Enroll: R=${R}: eid=${eid}"
   
   R=`SERVICE_URI=$ENROLL_URI ../skel-enroll/enroll-email.sh ${eid} andriyk@gmail.com`
   echo "Enroll: R=${R}"

else
   echo "User: $uid"
fi
