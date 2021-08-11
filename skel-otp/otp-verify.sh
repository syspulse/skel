#!/bin/bash

CODE=${1}
OTP=${2}
SERVICE_URI=${3:-http://127.0.0.1:8080/api/v1/otp}

DATA_JSON="{\"secret\":\"$SECRET\",\"userId\":\"$USER\",\"name\":\"$NAME\",\"account\":\"$ACCOUNT\"}"

curl -s -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/${OTP}/code/${CODE}
