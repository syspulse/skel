#docker build -t s3-mount .
docker buildx build --platform=linux/arm64,linux/amd64 -t s3-mount . 

#docker tag s3-mount openjdk-s3fs:18-slim
docker tag s3-mount openjdk-s3fs:11-slim
