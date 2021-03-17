#!/bin/bash

REPO=syspulse
NAME=skel-world
IMAGE=$REPO/$NAME
VERSION=${VERSION:-0.0.1}

AWS_ECR=341307817445.dkr.ecr.eu-west-1.amazonaws.com
AWS_REPO=bip
AWS_REGION=${AWS_RESION:-eu-west-1}


# push to ECR
docker tag $IMAGE $AWS_ECR/$AWS_REPO/$NAME:$VERSION
aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $AWS_ECR
docker push $AWS_ECR/$AWS_REPO/$NAME:$VERSION

