#!/bin/bash

docker ps -a|grep skel-shop|awk '{print $1}'|xargs docker rm
docker images|grep skel-shop|awk '{print $1}'|xargs docker rmi


