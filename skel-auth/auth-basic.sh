#!/bin/bash

CRED=${1:-user1:pass1}

curl -i -v -u $CRED http://localhost:8080/api/v1/auth
