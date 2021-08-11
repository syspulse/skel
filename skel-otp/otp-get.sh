#!/bin/bash

OTP=${1}
SERVICE_URI=${2:-http://127.0.0.1:8080/api/v1/otp}

curl -s -X GET -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" $SERVICE_URI/${OTP}
