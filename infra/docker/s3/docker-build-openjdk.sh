#!/usr/bin/env bash
set -euo pipefail

IMAGE=openjdk:21-skel
IMAGE_COMPAT=openjdk:21-slim
IMAGE_FULL=openjdk:21-skel-full
IMAGE_FULL_COMPAT=openjdk:21-slim-full

docker build -f Dockerfile.openjdk -t "$IMAGE" . "$@"
docker tag "$IMAGE" "$IMAGE_COMPAT"

docker build -f Dockerfile.openjdk.full -t "$IMAGE_FULL" . "$@"
docker tag "$IMAGE_FULL" "$IMAGE_FULL_COMPAT"

docker run --rm "$IMAGE_COMPAT" java -version
docker run --rm "$IMAGE_FULL_COMPAT" java -version
