#!/bin/bash

NAME=skel-world

DOCKER=syspulse/$NAME:0.0.1

# run with args
#docker run -p 8080:8080 $DOCKER --host=0.0.0.0

# run with mounted application.conf
docker rm $NAME >/dev/null
docker run --name "$NAME" -p 8083:8080 -v `pwd`/conf:/app/conf "$DOCKER"

# run with EnvVar
#docker run -e HOST=0.0.0.0 -p 8080:8080 -v `pwd`/docker_conf:/app/conf $DOCKER
