#!/bin/bash

DOCKER=skel-http

docker ps -a|grep $DOCKER|awk '{print $1}'|xargs docker rm
docker images|grep $DOCKER|awk '{print $1":"$2}'|xargs docker rmi


