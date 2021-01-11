#!/bin/bash

curl -H "Content-type: application/json" -X POST -d '{"secret": "123456", "name": "app3", "uri": "http://1"}' http://localhost:8080/api/v1/otp

curl http://localhost:8080/api/v1/otp

#curl http://localhost:8080/api/v1/otp/a5cca1c1-2266-47f0-b211
