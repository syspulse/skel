#FROM openjdk:18-slim
#FROM openjdk:11-slim
FROM openjdk:21-slim

ENV MNT_POINT /mnt/s3
ARG S3FS_VERSION=v1.86

RUN apt-get update -qq
RUN apt-get install s3fs -y

RUN mkdir -p "$MNT_POINT"
RUN chmod -R 777 /mnt

COPY mount-s3.sh /mount-s3.sh
RUN chmod +x /mount-s3.sh

CMD /mount-s3.sh
