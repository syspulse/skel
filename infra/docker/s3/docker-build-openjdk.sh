#!/usr/bin/env bash
set -euo pipefail

IMAGE=openjdk:21-skel
IMAGE_COMPAT=openjdk:21-slim

docker build -f Dockerfile.openjdk -t $IMAGE . "$@"
docker tag $IMAGE $IMAGE_COMPAT

docker run --rm $IMAGE_COMPAT java -version
