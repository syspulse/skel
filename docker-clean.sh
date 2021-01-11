#!/bin/bash

docker ps -a|grep skeleton-http|awk '{print $1}'|xargs docker rm
docker images|grep skeleton-http|awk '{print $1}'|xargs docker rmi
