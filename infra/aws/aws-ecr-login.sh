#!/bin/bash

AWS_ACCOUNT=${AWS_ACCOUNT:-649502643044}
AWS_REGION=${AWS_REGION:-eu-west-1}
aws ecr get-login-password --region eu-west-1 | docker login --username AWS --password-stdin $AWS_ACCOUNT.dkr.ecr.${AWS_REGION}.amazonaws.com
