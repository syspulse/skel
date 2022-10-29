FROM openjdk:18-slim

ENV MNT_POINT /mnt/s3
ARG S3FS_VERSION=v1.86

RUN apt-get update -qq
RUN apt-get install s3fs -y

RUN mkdir -p "$MNT_POINT"

COPY run.sh /run.sh
RUN chmod +x /run.sh

CMD /run.sh
