#!/bin/bash

docker ps -a|grep skel-world | awk '{print $1}'|xargs docker rm
docker images|grep skel-world | awk '{print $1}'|xargs docker rmi


