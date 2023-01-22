#!/bin/bash

ID=${1:-00000000-0000-0000-1000-000000000001}
AVATAR=${2:-avatar-1.png}

SERVICE_URI=${SERVICE_URI:-http://127.0.0.1:8080/api/v1/user}
ACCESS_TOKEN=${TOKEN-`cat ACCESS_TOKEN`}

curl -i -X POST -H "Content-Type: multipart/form-data" -F "fileUpload=@$AVATAR" -H "Authorization: Bearer $ACCESS_TOKEN" $SERVICE_URI/${ID}/avatar
