#!/bin/bash

docker ps -a|grep skel-http|awk '{print $1}'|xargs docker rm
docker images|grep skel-http|awk '{print $1":"$2}'|xargs docker rmi


