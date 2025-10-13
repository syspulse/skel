docker build -t s3-mount . $@

# https://github.com/moby/buildkit/issues/2343
#docker buildx build --platform=linux/arm64,linux/amd64 -t s3-mount . --load $@

#docker tag s3-mount openjdk-s3fs:18-slim
#docker tag s3-mount openjdk-s3fs:11-slim
docker tag s3-mount openjdk-s3fs:21-slim 
