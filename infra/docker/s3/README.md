# S3

## Docker with s3fs

### Build image

```
docker build -t s3-mount .
docker tag s3-mount openjdk-s3fs:11-slim
```

__NOTE__: Image must be from openjdk-11 or JavaScript Nashhorn is not working !

### Run 

```
docker run -it -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY -e S3_BUCKET=haas-data-dev --privileged s3-mount:latest
```

Check the mount:
```
ls -l /mnt/s3/
```
