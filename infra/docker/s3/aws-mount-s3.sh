MNT_POINT=${MNT_POINT:-/mnt/s3}

mount-s3 --uid 1000 --gid 1000  --region $AWS_REGION $S3_BUCKET $MNT_PINT
